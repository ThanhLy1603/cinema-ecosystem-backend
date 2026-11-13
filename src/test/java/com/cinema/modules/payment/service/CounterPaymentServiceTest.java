package com.cinema.modules.payment.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.PayableBookingResponse;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SettlementOutcome;
import com.cinema.modules.booking.event.CounterReceiptEmailRequestedEvent;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.IBookingSettlementService;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.payment.dto.request.CounterCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterReceiptResponse;
import com.cinema.modules.payment.entity.Payment;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.enums.PaymentStatus;
import com.cinema.modules.payment.repository.PaymentRepository;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");
    private static final Long STAFF_USER_ID = 3L;
    private static final Long CINEMA_ID = 1L;
    private static final Long BOOKING_ID = 100L;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IBookingSettlementService bookingSettlementService;

    @Mock
    private IBookingDetailService bookingDetailService;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private IUserQueryService userQueryService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private CounterPaymentService counterPaymentService;

    @BeforeEach
    void setUp() {
        counterPaymentService = new CounterPaymentService(
                paymentRepository,
                bookingSettlementService,
                bookingDetailService,
                branchAccessService,
                userQueryService,
                applicationEventPublisher,
                auditLogService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(bookingSettlementService.requirePayableBooking(STAFF_USER_ID)).thenReturn(
                new PayableBookingResponse(BOOKING_ID, "BK123", CINEMA_ID, new BigDecimal("180000.00"), NOW.plusSeconds(600)));
        lenient().when(bookingSettlementService.settleCounterBooking(anyLong(), any(), anyLong()))
                .thenReturn(SettlementOutcome.PAID);
        lenient().when(userQueryService.findContact(STAFF_USER_ID))
                .thenReturn(Optional.of(new UserContactResponse(STAFF_USER_ID, "staff@cinema.vn", "Lê Soát Vé")));
        lenient().when(userQueryService.findContact(9L))
                .thenReturn(Optional.of(new UserContactResponse(9L, "khach@mail.vn", "Nguyễn Khách")));
        lenient().when(bookingDetailService.findBookingDetail(BOOKING_ID))
                .thenReturn(Optional.of(buildBookingDetail()));
    }

    @Test
    void cashPaymentComputesChangeAndRecordsASuccessfulPayment() {
        CounterReceiptResponse counterReceiptResponse = counterPaymentService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CASH, new BigDecimal("200000"), null, null));

        assertEquals(new BigDecimal("20000.00"), counterReceiptResponse.changeAmount());
        assertEquals(new BigDecimal("200000"), counterReceiptResponse.cashReceived());
        assertEquals("Lê Soát Vé", counterReceiptResponse.soldByStaffName());
        assertNull(counterReceiptResponse.customerName());
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertEquals(PaymentMethod.CASH, payment.getPaymentMethod());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(new BigDecimal("180000.00"), payment.getAmount());
        assertEquals("BK123-POS", payment.getTransactionRef());
        assertEquals("20000.00", payment.getResponseData().get("changeAmount"));
        assertEquals(NOW, payment.getPaidAt());
        verify(bookingSettlementService).settleCounterBooking(BOOKING_ID, null, STAFF_USER_ID);
    }

    @Test
    void cashLessThanTheTotalIsRejectedBeforeAnythingIsSettled() {
        AppException appException = assertThrows(AppException.class, () -> counterPaymentService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CASH, new BigDecimal("100000"), null, null)));

        assertEquals("CASH_NOT_ENOUGH", appException.getErrorCode());
        verify(bookingSettlementService, never()).settleCounterBooking(anyLong(), any(), anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void cashWithoutAnAmountIsRejected() {
        AppException appException = assertThrows(AppException.class,
                () -> counterPaymentService.checkout(STAFF_USER_ID, request(PaymentMethod.CASH, null, null, null)));

        assertEquals("CASH_RECEIVED_REQUIRED", appException.getErrorCode());
    }

    @Test
    void cardPaymentNeedsNoCashAmountAndHasNoChange() {
        CounterReceiptResponse counterReceiptResponse = counterPaymentService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CARD, null, null, null));

        assertNull(counterReceiptResponse.changeAmount());
        assertNull(counterReceiptResponse.cashReceived());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void onlineGatewayMethodIsNotAllowedAtTheCounter() {
        AppException appException = assertThrows(AppException.class,
                () -> counterPaymentService.checkout(STAFF_USER_ID, request(PaymentMethod.VNPAY, null, null, null)));

        assertEquals("PAYMENT_METHOD_INVALID", appException.getErrorCode());
        verify(bookingSettlementService, never()).requirePayableBooking(anyLong());
    }

    @Test
    void linkedCustomerBecomesTheBookingOwner() {
        CounterReceiptResponse counterReceiptResponse = counterPaymentService.checkout(
                STAFF_USER_ID, request(PaymentMethod.TRANSFER, null, 9L, null));

        assertEquals("Nguyễn Khách", counterReceiptResponse.customerName());
        verify(bookingSettlementService).settleCounterBooking(BOOKING_ID, 9L, STAFF_USER_ID);
    }

    @Test
    void unknownCustomerIsRejected() {
        when(userQueryService.findContact(77L)).thenReturn(Optional.empty());

        AppException appException = assertThrows(AppException.class,
                () -> counterPaymentService.checkout(STAFF_USER_ID, request(PaymentMethod.CARD, null, 77L, null)));

        assertEquals(HttpStatus.NOT_FOUND, appException.getStatus());
        verify(bookingSettlementService, never()).settleCounterBooking(anyLong(), any(), anyLong());
    }

    @Test
    void bookingFromAnotherBranchIsRejected() {
        AppException appException = assertThrows(AppException.class, () -> counterPaymentService.checkout(
                STAFF_USER_ID, new CounterCheckoutRequest(2L, PaymentMethod.CARD, null, null, null)));

        assertEquals("WRONG_BRANCH", appException.getErrorCode());
        verify(bookingSettlementService, never()).settleCounterBooking(anyLong(), any(), anyLong());
    }

    @Test
    void settlementFailureAbortsWithoutRecordingAPayment() {
        when(bookingSettlementService.settleCounterBooking(anyLong(), any(), anyLong()))
                .thenReturn(SettlementOutcome.REFUND_REQUIRED);

        AppException appException = assertThrows(AppException.class,
                () -> counterPaymentService.checkout(STAFF_USER_ID, request(PaymentMethod.CARD, null, null, null)));

        assertEquals("COUNTER_SETTLEMENT_FAILED", appException.getErrorCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void receiptEmailPublishesAMailEventAndBlankEmailDoesNot() {
        counterPaymentService.checkout(
                STAFF_USER_ID, request(PaymentMethod.CARD, null, null, " khach@mail.vn "));
        counterPaymentService.checkout(STAFF_USER_ID, request(PaymentMethod.CARD, null, null, "  "));

        verify(applicationEventPublisher)
                .publishEvent(new CounterReceiptEmailRequestedEvent(BOOKING_ID, "khach@mail.vn"));
        verify(applicationEventPublisher, org.mockito.Mockito.times(1)).publishEvent(any(Object.class));
    }

    private CounterCheckoutRequest request(
            PaymentMethod paymentMethod, BigDecimal cashReceived, Long customerUserId, String receiptEmail) {
        return new CounterCheckoutRequest(CINEMA_ID, paymentMethod, cashReceived, customerUserId, receiptEmail);
    }

    private BookingDetailResponse buildBookingDetail() {
        BookingShowtimeResponse showtime = new BookingShowtimeResponse(
                20L, NOW, NOW.plusSeconds(7200), 3L, "phim", "Phim", null, AgeRating.P, 120, CINEMA_ID,
                "Star Cineplex", 5L, "Phòng 1", ScreenType.SCREEN_2D);
        return new BookingDetailResponse(
                BOOKING_ID, "BK123", BookingStatus.PAID, NOW, null, NOW, showtime, List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("180000"),
                new BigDecimal("180000"), List.of(), null);
    }
}
