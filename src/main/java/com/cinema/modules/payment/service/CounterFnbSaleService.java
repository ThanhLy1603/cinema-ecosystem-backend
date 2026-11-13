package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.request.BookingFnbLineRequest;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.fnb.dto.response.FnbProductInfoResponse;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import com.cinema.modules.payment.dto.request.CounterFnbCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptLineResponse;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptResponse;
import com.cinema.modules.payment.entity.CounterFnbOrder;
import com.cinema.modules.payment.entity.CounterFnbOrderLine;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.repository.CounterFnbOrderLineRepository;
import com.cinema.modules.payment.repository.CounterFnbOrderRepository;
import com.cinema.modules.payment.service.interfaces.ICounterFnbSaleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CounterFnbSaleService implements ICounterFnbSaleService {

    static final String ORDER_CODE_PREFIX = "FB";
    private static final int ORDER_CODE_RANDOM_LENGTH = 12;
    private static final Set<PaymentMethod> COUNTER_PAYMENT_METHODS =
            Set.of(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER);

    private final CounterFnbOrderRepository counterFnbOrderRepository;
    private final CounterFnbOrderLineRepository counterFnbOrderLineRepository;
    private final IFnbQueryService fnbQueryService;
    private final IBranchAccessService branchAccessService;
    private final IUserQueryService userQueryService;
    private final ICinemaQueryService cinemaQueryService;
    private final IAuditLogService auditLogService;
    private final Clock clock;

    public CounterFnbSaleService(
            CounterFnbOrderRepository counterFnbOrderRepository,
            CounterFnbOrderLineRepository counterFnbOrderLineRepository,
            IFnbQueryService fnbQueryService,
            IBranchAccessService branchAccessService,
            IUserQueryService userQueryService,
            ICinemaQueryService cinemaQueryService,
            IAuditLogService auditLogService,
            Clock clock) {
        this.counterFnbOrderRepository = counterFnbOrderRepository;
        this.counterFnbOrderLineRepository = counterFnbOrderLineRepository;
        this.fnbQueryService = fnbQueryService;
        this.branchAccessService = branchAccessService;
        this.userQueryService = userQueryService;
        this.cinemaQueryService = cinemaQueryService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    @Override
    public CounterFnbReceiptResponse checkout(Long staffUserId, CounterFnbCheckoutRequest counterFnbCheckoutRequest) {
        branchAccessService.assertCanAccessCinema(staffUserId, counterFnbCheckoutRequest.cinemaId());
        PaymentMethod paymentMethod = counterFnbCheckoutRequest.paymentMethod();
        if (!COUNTER_PAYMENT_METHODS.contains(paymentMethod)) {
            throw new AppException(
                    "Phương thức thanh toán không dùng được tại quầy", HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_INVALID");
        }
        List<CounterFnbOrderLine> counterFnbOrderLines = buildOrderLines(counterFnbCheckoutRequest.lines());
        BigDecimal totalAmount = counterFnbOrderLines.stream()
                .map(counterFnbOrderLine -> counterFnbOrderLine.getUnitPrice()
                        .multiply(BigDecimal.valueOf(counterFnbOrderLine.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.signum() <= 0) {
            throw new AppException(
                    "Đơn bắp nước phải có giá trị lớn hơn 0", HttpStatus.BAD_REQUEST, "FNB_ORDER_EMPTY");
        }
        BigDecimal cashReceived = null;
        BigDecimal changeAmount = null;
        if (paymentMethod == PaymentMethod.CASH) {
            cashReceived = counterFnbCheckoutRequest.cashReceived();
            if (cashReceived == null) {
                throw new AppException(
                        "Vui lòng nhập số tiền khách đưa", HttpStatus.BAD_REQUEST, "CASH_RECEIVED_REQUIRED");
            }
            if (cashReceived.compareTo(totalAmount) < 0) {
                throw new AppException(
                        "Số tiền khách đưa chưa đủ để thanh toán", HttpStatus.BAD_REQUEST, "CASH_NOT_ENOUGH");
            }
            changeAmount = cashReceived.subtract(totalAmount);
        }
        Instant now = Instant.now(clock);
        CounterFnbOrder counterFnbOrder = new CounterFnbOrder();
        counterFnbOrder.setOrderCode(generateOrderCode());
        counterFnbOrder.setCinemaId(counterFnbCheckoutRequest.cinemaId());
        counterFnbOrder.setSoldByStaffUserId(staffUserId);
        counterFnbOrder.setTotalAmount(totalAmount);
        counterFnbOrder.setPaymentMethod(paymentMethod);
        counterFnbOrder.setCashReceived(cashReceived);
        counterFnbOrder.setChangeAmount(changeAmount);
        counterFnbOrder.setCreatedAt(now);
        CounterFnbOrder savedCounterFnbOrder = counterFnbOrderRepository.save(counterFnbOrder);
        counterFnbOrderLines.forEach(counterFnbOrderLine -> counterFnbOrderLine.setOrderId(savedCounterFnbOrder.getId()));
        List<CounterFnbOrderLine> savedCounterFnbOrderLines = counterFnbOrderLineRepository.saveAll(counterFnbOrderLines);
        auditLogService.record(staffUserId, counterFnbCheckoutRequest.cinemaId(), AuditAction.COUNTER_FNB_SALE,
                "FNB_ORDER", savedCounterFnbOrder.getOrderCode(), paymentMethod + " " + totalAmount.toPlainString());
        return toReceipt(savedCounterFnbOrder, savedCounterFnbOrderLines);
    }

    @Transactional(readOnly = true)
    @Override
    public CounterFnbReceiptResponse findReceipt(Long staffUserId, String orderCode) {
        CounterFnbOrder counterFnbOrder = counterFnbOrderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(
                        "Không tìm thấy đơn bắp nước", HttpStatus.NOT_FOUND, "FNB_ORDER_NOT_FOUND"));
        branchAccessService.assertCanAccessCinema(staffUserId, counterFnbOrder.getCinemaId());
        return toReceipt(
                counterFnbOrder, counterFnbOrderLineRepository.findByOrderIdOrderByIdAsc(counterFnbOrder.getId()));
    }

    private List<CounterFnbOrderLine> buildOrderLines(List<BookingFnbLineRequest> bookingFnbLineRequests) {
        Set<String> lineKeys = new HashSet<>();
        for (BookingFnbLineRequest bookingFnbLineRequest : bookingFnbLineRequests) {
            if ((bookingFnbLineRequest.comboId() == null) == (bookingFnbLineRequest.itemId() == null)) {
                throw new AppException(
                        "Mỗi dòng phải chọn đúng một combo hoặc một món", HttpStatus.BAD_REQUEST, "FNB_LINE_INVALID");
            }
            String lineKey = bookingFnbLineRequest.comboId() != null
                    ? "COMBO-" + bookingFnbLineRequest.comboId()
                    : "ITEM-" + bookingFnbLineRequest.itemId();
            if (!lineKeys.add(lineKey)) {
                throw new AppException(
                        "Mỗi combo hoặc món chỉ được xuất hiện một lần", HttpStatus.BAD_REQUEST, "FNB_DUPLICATE_LINE");
            }
        }
        Map<Long, FnbProductInfoResponse> comboInfos = fnbQueryService.findComboInfos(
                bookingFnbLineRequests.stream().map(BookingFnbLineRequest::comboId).filter(Objects::nonNull).toList());
        Map<Long, FnbProductInfoResponse> itemInfos = fnbQueryService.findItemInfos(
                bookingFnbLineRequests.stream().map(BookingFnbLineRequest::itemId).filter(Objects::nonNull).toList());
        return bookingFnbLineRequests.stream()
                .map(bookingFnbLineRequest -> toOrderLine(bookingFnbLineRequest, comboInfos, itemInfos))
                .toList();
    }

    private CounterFnbOrderLine toOrderLine(
            BookingFnbLineRequest bookingFnbLineRequest,
            Map<Long, FnbProductInfoResponse> comboInfos,
            Map<Long, FnbProductInfoResponse> itemInfos) {
        FnbProductInfoResponse fnbProductInfoResponse = bookingFnbLineRequest.comboId() != null
                ? comboInfos.get(bookingFnbLineRequest.comboId())
                : itemInfos.get(bookingFnbLineRequest.itemId());
        if (fnbProductInfoResponse == null) {
            throw new AppException("Không tìm thấy combo hoặc món đã chọn", HttpStatus.NOT_FOUND, "FNB_PRODUCT_NOT_FOUND");
        }
        if (!fnbProductInfoResponse.orderable()) {
            throw new AppException(
                    "\"" + fnbProductInfoResponse.name() + "\" hiện không còn bán",
                    HttpStatus.CONFLICT,
                    "FNB_PRODUCT_UNAVAILABLE");
        }
        CounterFnbOrderLine counterFnbOrderLine = new CounterFnbOrderLine();
        counterFnbOrderLine.setComboId(bookingFnbLineRequest.comboId());
        counterFnbOrderLine.setItemId(bookingFnbLineRequest.itemId());
        counterFnbOrderLine.setProductName(fnbProductInfoResponse.name());
        counterFnbOrderLine.setQuantity(bookingFnbLineRequest.quantity());
        counterFnbOrderLine.setUnitPrice(fnbProductInfoResponse.price());
        return counterFnbOrderLine;
    }

    private CounterFnbReceiptResponse toReceipt(
            CounterFnbOrder counterFnbOrder, List<CounterFnbOrderLine> counterFnbOrderLines) {
        List<CounterFnbReceiptLineResponse> counterFnbReceiptLineResponses = counterFnbOrderLines.stream()
                .map(counterFnbOrderLine -> new CounterFnbReceiptLineResponse(
                        counterFnbOrderLine.getComboId(),
                        counterFnbOrderLine.getItemId(),
                        counterFnbOrderLine.getProductName(),
                        counterFnbOrderLine.getQuantity(),
                        counterFnbOrderLine.getUnitPrice(),
                        counterFnbOrderLine.getUnitPrice()
                                .multiply(BigDecimal.valueOf(counterFnbOrderLine.getQuantity()))))
                .toList();
        return new CounterFnbReceiptResponse(
                counterFnbOrder.getOrderCode(),
                counterFnbOrder.getCinemaId(),
                cinemaQueryService.findCinemaNames(List.of(counterFnbOrder.getCinemaId()))
                        .getOrDefault(counterFnbOrder.getCinemaId(), ""),
                counterFnbReceiptLineResponses,
                counterFnbOrder.getTotalAmount(),
                counterFnbOrder.getPaymentMethod(),
                counterFnbOrder.getCashReceived(),
                counterFnbOrder.getChangeAmount(),
                userQueryService.findContact(counterFnbOrder.getSoldByStaffUserId())
                        .map(UserContactResponse::fullName)
                        .orElse(null),
                counterFnbOrder.getCreatedAt());
    }

    private String generateOrderCode() {
        return ORDER_CODE_PREFIX
                + UUID.randomUUID().toString().replace("-", "").substring(0, ORDER_CODE_RANDOM_LENGTH).toUpperCase();
    }
}
