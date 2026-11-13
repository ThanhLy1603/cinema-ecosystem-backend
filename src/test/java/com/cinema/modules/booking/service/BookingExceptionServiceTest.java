package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingExceptionResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.BookingRefund;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SalesChannel;
import com.cinema.modules.booking.event.BookingRefundedEvent;
import com.cinema.modules.booking.event.TicketReissuedEvent;
import com.cinema.modules.booking.repository.BookingRefundRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.payment.dto.response.RefundedPaymentResponse;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.service.interfaces.IPaymentQueryService;
import com.cinema.modules.payment.service.interfaces.IPaymentRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExceptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");
    private static final Long MANAGER_USER_ID = 8L;
    private static final Long CINEMA_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final String BOOKING_CODE = "BK123";

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @Mock
    private BookingRefundRepository bookingRefundRepository;

    @Mock
    private IBookingDetailService bookingDetailService;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private IPaymentQueryService paymentQueryService;

    @Mock
    private IPaymentRefundService paymentRefundService;

    @Mock
    private ISeatStatusBroadcaster seatStatusBroadcaster;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private TicketCodeGenerator ticketCodeGenerator;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private BookingExceptionService bookingExceptionService;
    private Booking booking;
    private Instant showtimeStart;
    private boolean ticketCheckedIn;
    private int deliveredQuantity;

    @BeforeEach
    void setUp() {
        bookingExceptionService = new BookingExceptionService(
                bookingRepository,
                ticketRepository,
                seatReservationRepository,
                bookingRefundRepository,
                bookingDetailService,
                branchAccessService,
                paymentQueryService,
                paymentRefundService,
                seatStatusBroadcaster,
                auditLogService,
                ticketCodeGenerator,
                applicationEventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setBookingCode(BOOKING_CODE);
        booking.setUserId(5L);
        booking.setCinemaId(CINEMA_ID);
        booking.setShowtimeId(20L);
        booking.setFinalAmount(new BigDecimal("180000"));
        booking.setStatus(BookingStatus.PAID);
        booking.setSalesChannel(SalesChannel.ONLINE);
        booking.setQrCode("QROLD");
        showtimeStart = NOW.plusSeconds(7200);
        ticketCheckedIn = false;
        deliveredQuantity = 0;
        lenient().when(bookingRepository.findByBookingCode(BOOKING_CODE)).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        lenient().when(bookingDetailService.findBookingDetail(BOOKING_ID)).thenAnswer(invocation ->
                Optional.of(detail()));
        lenient().when(bookingRefundRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());
        lenient().when(paymentQueryService.findSuccessfulPaymentMethod(BOOKING_ID))
                .thenReturn(Optional.of(PaymentMethod.VNPAY));
        lenient().when(ticketCodeGenerator.generateQrCode()).thenReturn("QRNEW");
        Ticket ticket = new Ticket();
        ticket.setSeatId(7L);
        lenient().when(ticketRepository.findByBookingIdOrderBySeatIdAsc(BOOKING_ID)).thenReturn(List.of(ticket));
        lenient().when(paymentRefundService.markBookingPaymentRefunded(BOOKING_ID))
                .thenReturn(Optional.of(new RefundedPaymentResponse(PaymentMethod.VNPAY, new BigDecimal("180000"))));
    }

    @Test
    void lookupReportsRefundableWhenPaidAndBeforeShowtime() {
        BookingExceptionResponse bookingExceptionResponse = bookingExceptionService.lookup(MANAGER_USER_ID, "bk123");

        assertTrue(bookingExceptionResponse.canRefund());
        assertTrue(bookingExceptionResponse.canReissue());
        verify(branchAccessService).assertCanAccessCinema(MANAGER_USER_ID, CINEMA_ID);
    }

    @Test
    void lookupBlocksRefundWhenTicketAlreadyCheckedIn() {
        ticketCheckedIn = true;

        BookingExceptionResponse bookingExceptionResponse = bookingExceptionService.lookup(MANAGER_USER_ID, BOOKING_CODE);

        assertFalse(bookingExceptionResponse.canRefund());
        assertEquals(1, bookingExceptionResponse.checkedInTicketCount());
        assertTrue(bookingExceptionResponse.refundBlockedReason().contains("hoàn tác soát vé"));
    }

    @Test
    void lookupBlocksRefundWhenFnbDelivered() {
        deliveredQuantity = 1;

        assertFalse(bookingExceptionService.lookup(MANAGER_USER_ID, BOOKING_CODE).canRefund());
    }

    @Test
    void lookupBlocksRefundAfterShowtimeStarted() {
        showtimeStart = NOW.minusSeconds(60);

        BookingExceptionResponse bookingExceptionResponse = bookingExceptionService.lookup(MANAGER_USER_ID, BOOKING_CODE);

        assertFalse(bookingExceptionResponse.canRefund());
        assertTrue(bookingExceptionResponse.canReissue());
    }

    @Test
    void refundReleasesSeatsMarksRefundedRecordsAndPublishesEvent() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            BookingExceptionResponse bookingExceptionResponse =
                    bookingExceptionService.refundBooking(MANAGER_USER_ID, BOOKING_CODE, "Khách đổi ý");

            assertEquals(BookingStatus.REFUNDED, booking.getStatus());
            assertFalse(bookingExceptionResponse.canRefund());
            verify(ticketRepository).deleteByBookingId(BOOKING_ID);
            verify(seatReservationRepository).deleteByBookingId(BOOKING_ID);
            ArgumentCaptor<BookingRefund> refundCaptor = ArgumentCaptor.forClass(BookingRefund.class);
            verify(bookingRefundRepository).save(refundCaptor.capture());
            assertEquals(new BigDecimal("180000"), refundCaptor.getValue().getAmount());
            assertEquals("VNPAY", refundCaptor.getValue().getPaymentMethod());
            assertEquals(MANAGER_USER_ID, refundCaptor.getValue().getRefundedByUserId());
            verify(applicationEventPublisher).publishEvent(
                    new BookingRefundedEvent(BOOKING_ID, 5L, BOOKING_CODE, new BigDecimal("180000")));
            verify(auditLogService).record(
                    eq(MANAGER_USER_ID), eq(CINEMA_ID), eq(AuditAction.REFUND_BOOKING), eq("BOOKING"),
                    eq(BOOKING_CODE), any());
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void refundIsRejectedWhenTicketCheckedIn() {
        ticketCheckedIn = true;

        AppException appException = assertThrows(AppException.class,
                () -> bookingExceptionService.refundBooking(MANAGER_USER_ID, BOOKING_CODE, "x"));

        assertEquals("NOT_REFUNDABLE", appException.getErrorCode());
        verify(ticketRepository, never()).deleteByBookingId(any());
        verify(bookingRefundRepository, never()).save(any());
    }

    @Test
    void reissueReplacesQrCodeAndPublishesEvent() {
        bookingExceptionService.reissueTicket(MANAGER_USER_ID, BOOKING_CODE, "Mất vé");

        assertEquals("QRNEW", booking.getQrCode());
        assertNotEquals("QROLD", booking.getQrCode());
        verify(applicationEventPublisher).publishEvent(new TicketReissuedEvent(BOOKING_ID, 5L, BOOKING_CODE));
        verify(auditLogService).record(
                eq(MANAGER_USER_ID), eq(CINEMA_ID), eq(AuditAction.REISSUE_TICKET), eq("BOOKING"), eq(BOOKING_CODE),
                eq("Mất vé"));
    }

    @Test
    void reissueIsRejectedForRefundedBooking() {
        booking.setStatus(BookingStatus.REFUNDED);

        AppException appException = assertThrows(AppException.class,
                () -> bookingExceptionService.reissueTicket(MANAGER_USER_ID, BOOKING_CODE, "x"));

        assertEquals("NOT_REISSUABLE", appException.getErrorCode());
        assertEquals("QROLD", booking.getQrCode());
    }

    private BookingDetailResponse detail() {
        BookingShowtimeResponse showtime = new BookingShowtimeResponse(
                20L, showtimeStart, showtimeStart.plusSeconds(7200), 3L, "phim", "Phim", null, AgeRating.P, 120,
                CINEMA_ID, "Star", 5L, "Phòng 1", ScreenType.SCREEN_2D);
        return new BookingDetailResponse(
                BOOKING_ID, BOOKING_CODE, booking.getStatus(), NOW, null, NOW, showtime, List.of(),
                List.of(new BookingFnbLineResponse(
                        1L, null, "Combo", null, 1, new BigDecimal("50000"), new BigDecimal("50000"),
                        deliveredQuantity, null)),
                new BigDecimal("130000"), new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("180000"),
                new BigDecimal("180000"),
                List.of(new BookingTicketResponse(
                        1L, "T1", 7L, "A1", new BigDecimal("130000"), ticketCheckedIn, ticketCheckedIn ? NOW : null)),
                null);
    }
}
