package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BusinessZone;
import com.cinema.config.properties.VnpayProperties;
import com.cinema.modules.booking.dto.response.PayableBookingResponse;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SettlementOutcome;
import com.cinema.modules.booking.service.interfaces.IBookingSettlementService;
import com.cinema.modules.payment.dto.request.SimulatePaymentRequest;
import com.cinema.modules.payment.dto.response.PaymentResultResponse;
import com.cinema.modules.payment.dto.response.PaymentUrlResponse;
import com.cinema.modules.payment.dto.response.VnpayIpnResponse;
import com.cinema.modules.payment.entity.Payment;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.enums.PaymentStatus;
import com.cinema.modules.payment.enums.SimulatedPaymentOutcome;
import com.cinema.modules.payment.repository.PaymentRepository;
import com.cinema.modules.payment.service.interfaces.IPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class PaymentService implements IPaymentService {

    static final String RSP_SUCCESS = "00";
    static final String RSP_ORDER_NOT_FOUND = "01";
    static final String RSP_INVALID_AMOUNT = "04";
    static final String RSP_INVALID_SIGNATURE = "97";
    static final String VNPAY_SUCCESS_CODE = "00";
    static final String VNPAY_FAILED_CODE = "24";
    private static final DateTimeFormatter VNPAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(BusinessZone.VIETNAM);
    private static final Duration MIN_REMAINING_HOLD = Duration.ofMinutes(2);
    private static final Duration HOLD_SAFETY_MARGIN = Duration.ofMinutes(1);
    private static final BigDecimal VNPAY_AMOUNT_FACTOR = BigDecimal.valueOf(100);

    private final PaymentRepository paymentRepository;
    private final IBookingSettlementService bookingSettlementService;
    private final VnpaySigner vnpaySigner;
    private final VnpayProperties vnpayProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            IBookingSettlementService bookingSettlementService,
            VnpaySigner vnpaySigner,
            VnpayProperties vnpayProperties,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.bookingSettlementService = bookingSettlementService;
        this.vnpaySigner = vnpaySigner;
        this.vnpayProperties = vnpayProperties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public PaymentUrlResponse createVnpayPayment(Long userId, String clientIp) {
        if (!vnpayProperties.isConfigured()) {
            throw new AppException(
                    "Cổng thanh toán chưa được cấu hình", HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED");
        }
        Instant now = Instant.now(clock);
        return transactionTemplate.execute(transactionStatus -> createPaymentInTransaction(userId, clientIp, now));
    }

    @Override
    public VnpayIpnResponse processVnpayIpn(Map<String, String> params) {
        return transactionTemplate.execute(transactionStatus -> processIpnInTransaction(params));
    }

    @Override
    public PaymentResultResponse getPaymentResult(Long userId, String transactionRef) {
        Payment payment = requireOwnedPayment(userId, transactionRef);
        BookingStatus bookingStatus = bookingSettlementService.findOwnedBookingStatus(payment.getBookingId(), userId)
                .orElseThrow(this::paymentNotFound);
        return new PaymentResultResponse(
                payment.getTransactionRef(), payment.getStatus(), payment.getBookingId(), bookingStatus, payment.getAmount());
    }

    @Override
    public VnpayIpnResponse simulateGatewayCallback(Long userId, SimulatePaymentRequest simulatePaymentRequest) {
        if (!vnpayProperties.simulatorEnabled()) {
            throw new AppException("Không tìm thấy tài nguyên", HttpStatus.NOT_FOUND, "SIMULATOR_DISABLED");
        }
        Payment payment = requireOwnedPayment(userId, simulatePaymentRequest.transactionRef());
        String responseCode = simulatePaymentRequest.outcome() == SimulatedPaymentOutcome.SUCCESS
                ? VNPAY_SUCCESS_CODE
                : VNPAY_FAILED_CODE;
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_TmnCode", vnpayProperties.tmnCode());
        params.put("vnp_Amount", toVnpayAmount(payment.getAmount()));
        params.put("vnp_TxnRef", payment.getTransactionRef());
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_TransactionNo", "SIM" + Instant.now(clock).toEpochMilli());
        params.put("vnp_BankCode", "SIMULATOR");
        params.put("vnp_PayDate", VNPAY_DATE_FORMATTER.format(Instant.now(clock)));
        params.put(VnpaySigner.SECURE_HASH_FIELD, vnpaySigner.sign(params));
        return processVnpayIpn(params);
    }

    private PaymentUrlResponse createPaymentInTransaction(Long userId, String clientIp, Instant now) {
        PayableBookingResponse payableBookingResponse = bookingSettlementService.requirePayableBooking(userId);
        Instant latestExpiry = payableBookingResponse.expiresAt().minus(HOLD_SAFETY_MARGIN);
        if (payableBookingResponse.expiresAt().isBefore(now.plus(MIN_REMAINING_HOLD))) {
            throw new AppException(
                    "Thời gian giữ ghế còn quá ít để thanh toán, vui lòng chọn lại ghế",
                    HttpStatus.CONFLICT,
                    "HOLD_EXPIRING_SOON");
        }
        Instant windowExpiry = now.plus(Duration.ofMinutes(Math.max(1, vnpayProperties.paymentWindowMinutes())));
        Instant paymentExpiry = windowExpiry.isBefore(latestExpiry) ? windowExpiry : latestExpiry;
        Payment payment = new Payment();
        payment.setBookingId(payableBookingResponse.bookingId());
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setTransactionRef(payableBookingResponse.bookingCode() + "-" + now.getEpochSecond());
        payment.setAmount(payableBookingResponse.finalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(now);
        paymentRepository.saveAndFlush(payment);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpayProperties.tmnCode());
        params.put("vnp_Amount", toVnpayAmount(payment.getAmount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getTransactionRef());
        params.put("vnp_OrderInfo", "Thanh toan ve " + payableBookingResponse.bookingCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnpayProperties.returnUrl());
        params.put("vnp_IpAddr", normalizeClientIp(clientIp));
        params.put("vnp_CreateDate", VNPAY_DATE_FORMATTER.format(now));
        params.put("vnp_ExpireDate", VNPAY_DATE_FORMATTER.format(paymentExpiry));
        return new PaymentUrlResponse(vnpaySigner.buildPayUrl(params), payment.getTransactionRef(), paymentExpiry);
    }

    private VnpayIpnResponse processIpnInTransaction(Map<String, String> params) {
        if (!vnpaySigner.verify(params)) {
            return new VnpayIpnResponse(RSP_INVALID_SIGNATURE, "Invalid Signature");
        }
        Payment payment = paymentRepository.findByTransactionRef(params.get("vnp_TxnRef")).orElse(null);
        if (payment == null) {
            return new VnpayIpnResponse(RSP_ORDER_NOT_FOUND, "Order not found");
        }
        if (!toVnpayAmount(payment.getAmount()).equals(params.get("vnp_Amount"))) {
            return new VnpayIpnResponse(RSP_INVALID_AMOUNT, "Invalid amount");
        }
        boolean gatewaySuccess = VNPAY_SUCCESS_CODE.equals(params.get("vnp_ResponseCode"))
                && VNPAY_SUCCESS_CODE.equals(params.get("vnp_TransactionStatus"));
        if (!gatewaySuccess) {
            recordResult(payment.getTransactionRef(), PaymentStatus.FAILED, params);
            return new VnpayIpnResponse(RSP_SUCCESS, "Confirm Success");
        }
        SettlementOutcome settlementOutcome = bookingSettlementService.settlePaidBooking(payment.getBookingId());
        recordResult(payment.getTransactionRef(), PaymentStatus.SUCCESS, params);
        if (settlementOutcome == SettlementOutcome.REFUND_REQUIRED) {
            log.warn("Giao dịch {} thành công nhưng đơn {} cần hoàn tiền", payment.getTransactionRef(), payment.getBookingId());
        }
        return new VnpayIpnResponse(
                RSP_SUCCESS,
                settlementOutcome == SettlementOutcome.ALREADY_PAID ? "Order already confirmed" : "Confirm Success");
    }

    private void recordResult(String transactionRef, PaymentStatus paymentStatus, Map<String, String> params) {
        Payment lockedPayment = paymentRepository.findByTransactionRefForUpdate(transactionRef).orElseThrow();
        if (lockedPayment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        lockedPayment.setStatus(paymentStatus);
        lockedPayment.setResponseData(new HashMap<>(params));
        if (paymentStatus == PaymentStatus.SUCCESS) {
            lockedPayment.setPaidAt(Instant.now(clock));
        }
        paymentRepository.save(lockedPayment);
    }

    private Payment requireOwnedPayment(Long userId, String transactionRef) {
        Payment payment = paymentRepository.findByTransactionRef(transactionRef).orElseThrow(this::paymentNotFound);
        bookingSettlementService.findOwnedBookingStatus(payment.getBookingId(), userId).orElseThrow(this::paymentNotFound);
        return payment;
    }

    private String toVnpayAmount(BigDecimal amount) {
        return amount.multiply(VNPAY_AMOUNT_FACTOR).setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || clientIp.contains(":")) {
            return "127.0.0.1";
        }
        return clientIp;
    }

    private AppException paymentNotFound() {
        return new AppException("Không tìm thấy giao dịch", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND");
    }
}
