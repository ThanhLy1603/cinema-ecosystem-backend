package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.PayableBookingResponse;
import com.cinema.modules.booking.enums.SettlementOutcome;
import com.cinema.modules.booking.event.CounterReceiptEmailRequestedEvent;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.IBookingSettlementService;
import com.cinema.modules.payment.dto.request.CounterCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterReceiptResponse;
import com.cinema.modules.payment.entity.Payment;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.enums.PaymentStatus;
import com.cinema.modules.payment.repository.PaymentRepository;
import com.cinema.modules.payment.service.interfaces.ICounterPaymentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class CounterPaymentService implements ICounterPaymentService {

    static final String TRANSACTION_REF_SUFFIX = "-POS";
    private static final Set<PaymentMethod> COUNTER_PAYMENT_METHODS =
            Set.of(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER);

    private final PaymentRepository paymentRepository;
    private final IBookingSettlementService bookingSettlementService;
    private final IBookingDetailService bookingDetailService;
    private final IBranchAccessService branchAccessService;
    private final IUserQueryService userQueryService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public CounterPaymentService(
            PaymentRepository paymentRepository,
            IBookingSettlementService bookingSettlementService,
            IBookingDetailService bookingDetailService,
            IBranchAccessService branchAccessService,
            IUserQueryService userQueryService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.bookingSettlementService = bookingSettlementService;
        this.bookingDetailService = bookingDetailService;
        this.branchAccessService = branchAccessService;
        this.userQueryService = userQueryService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    @Override
    public CounterReceiptResponse checkout(Long staffUserId, CounterCheckoutRequest counterCheckoutRequest) {
        branchAccessService.assertCanAccessCinema(staffUserId, counterCheckoutRequest.cinemaId());
        PaymentMethod paymentMethod = counterCheckoutRequest.paymentMethod();
        if (!COUNTER_PAYMENT_METHODS.contains(paymentMethod)) {
            throw new AppException(
                    "Phương thức thanh toán không dùng được tại quầy", HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_INVALID");
        }
        PayableBookingResponse payableBookingResponse = bookingSettlementService.requirePayableBooking(staffUserId);
        if (!payableBookingResponse.cinemaId().equals(counterCheckoutRequest.cinemaId())) {
            throw new AppException(
                    "Đơn đang bán thuộc chi nhánh khác", HttpStatus.CONFLICT, "WRONG_BRANCH");
        }
        UserContactResponse customerContactResponse = counterCheckoutRequest.customerUserId() == null
                ? null
                : userQueryService.findContact(counterCheckoutRequest.customerUserId())
                        .orElseThrow(() -> new AppException(
                                "Không tìm thấy khách hàng", HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND"));
        BigDecimal finalAmount = payableBookingResponse.finalAmount();
        BigDecimal cashReceived = null;
        BigDecimal changeAmount = null;
        if (paymentMethod == PaymentMethod.CASH) {
            cashReceived = counterCheckoutRequest.cashReceived();
            if (cashReceived == null) {
                throw new AppException(
                        "Vui lòng nhập số tiền khách đưa", HttpStatus.BAD_REQUEST, "CASH_RECEIVED_REQUIRED");
            }
            if (cashReceived.compareTo(finalAmount) < 0) {
                throw new AppException(
                        "Số tiền khách đưa chưa đủ để thanh toán", HttpStatus.BAD_REQUEST, "CASH_NOT_ENOUGH");
            }
            changeAmount = cashReceived.subtract(finalAmount);
        }
        SettlementOutcome settlementOutcome = bookingSettlementService.settleCounterBooking(
                payableBookingResponse.bookingId(),
                customerContactResponse == null ? null : customerContactResponse.id(),
                staffUserId);
        if (settlementOutcome != SettlementOutcome.PAID) {
            throw new AppException(
                    "Không thể chốt đơn vì ghế không còn được giữ, vui lòng chọn lại ghế",
                    HttpStatus.CONFLICT,
                    "COUNTER_SETTLEMENT_FAILED");
        }
        Instant now = Instant.now(clock);
        paymentRepository.save(buildPayment(
                payableBookingResponse, paymentMethod, staffUserId, cashReceived, changeAmount, now));
        if (counterCheckoutRequest.receiptEmail() != null && !counterCheckoutRequest.receiptEmail().isBlank()) {
            applicationEventPublisher.publishEvent(new CounterReceiptEmailRequestedEvent(
                    payableBookingResponse.bookingId(), counterCheckoutRequest.receiptEmail().trim()));
        }
        BookingDetailResponse bookingDetailResponse = bookingDetailService
                .findBookingDetail(payableBookingResponse.bookingId())
                .orElseThrow(() -> new AppException(
                        "Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
        return new CounterReceiptResponse(
                bookingDetailResponse,
                paymentMethod,
                cashReceived,
                changeAmount,
                userQueryService.findContact(staffUserId).map(UserContactResponse::fullName).orElse(null),
                customerContactResponse == null ? null : customerContactResponse.fullName(),
                now);
    }

    private Payment buildPayment(
            PayableBookingResponse payableBookingResponse,
            PaymentMethod paymentMethod,
            Long staffUserId,
            BigDecimal cashReceived,
            BigDecimal changeAmount,
            Instant now) {
        Map<String, String> responseData = new HashMap<>();
        responseData.put("channel", "COUNTER");
        responseData.put("staffUserId", String.valueOf(staffUserId));
        if (cashReceived != null) {
            responseData.put("cashReceived", cashReceived.toPlainString());
            responseData.put("changeAmount", changeAmount.toPlainString());
        }
        Payment payment = new Payment();
        payment.setBookingId(payableBookingResponse.bookingId());
        payment.setPaymentMethod(paymentMethod);
        payment.setTransactionRef(payableBookingResponse.bookingCode() + TRANSACTION_REF_SUFFIX);
        payment.setAmount(payableBookingResponse.finalAmount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setResponseData(responseData);
        payment.setPaidAt(now);
        payment.setCreatedAt(now);
        return payment;
    }
}
