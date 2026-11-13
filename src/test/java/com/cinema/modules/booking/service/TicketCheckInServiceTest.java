package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.booking.dto.request.TicketCheckInRequest;
import com.cinema.modules.booking.dto.request.TicketScanRequest;
import com.cinema.modules.booking.dto.request.TicketUndoRequest;
import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.booking.dto.response.BookingFnbLineResponse;
import com.cinema.modules.booking.dto.response.BookingSeatResponse;
import com.cinema.modules.booking.dto.response.BookingShowtimeResponse;
import com.cinema.modules.booking.dto.response.BookingTicketResponse;
import com.cinema.modules.booking.dto.response.TicketScanResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.entity.TicketScanLog;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.CheckInResultCode;
import com.cinema.modules.booking.enums.TicketScanAction;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.repository.TicketScanLogRepository;
import com.cinema.modules.booking.service.interfaces.IBookingDetailService;
import com.cinema.modules.fnb.service.interfaces.IFnbQueryService;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.service.interfaces.IPaymentQueryService;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.audit.enums.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCheckInServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T13:00:00Z");
    private static final Instant SHOWTIME_START = Instant.parse("2026-11-12T13:30:00Z");
    private static final Instant SHOWTIME_END = Instant.parse("2026-11-12T15:30:00Z");
    private static final Long STAFF_USER_ID = 7L;
    private static final Long CINEMA_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final String QR_CODE = "QRABCDEFGHJKLMNPQRSTUVWX";

    @Mock
    private ScanGuard scanGuard;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketScanLogRepository ticketScanLogRepository;

    @Mock
    private IBookingDetailService bookingDetailService;

    @Mock
    private IBranchAccessService branchAccessService;

    @Mock
    private IUserQueryService userQueryService;

    @Mock
    private IFnbQueryService fnbQueryService;

    @Mock
    private IPaymentQueryService paymentQueryService;

    private TicketCheckInService ticketCheckInService;
    private Booking booking;
    private List<Ticket> tickets;
    private Instant clockInstant = NOW;

    @BeforeEach
    void setUp() {
        Clock mutableClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return clockInstant;
            }
        };
        ticketCheckInService = new TicketCheckInService(
                bookingRepository,
                ticketRepository,
                ticketScanLogRepository,
                bookingDetailService,
                branchAccessService,
                userQueryService,
                fnbQueryService,
                paymentQueryService,
                scanGuard,
                auditLogService,
                mutableClock);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setBookingCode("BK62072B12C606");
        booking.setCinemaId(CINEMA_ID);
        booking.setStatus(BookingStatus.PAID);
        booking.setQrCode(QR_CODE);
        tickets = new ArrayList<>(List.of(buildTicket(1L, 11L), buildTicket(2L, 12L)));

        lenient().when(bookingRepository.findByQrCode(QR_CODE)).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findByBookingCode("BK62072B12C606")).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        lenient().when(ticketRepository.findByBookingIdOrderBySeatIdAsc(BOOKING_ID)).thenReturn(tickets);
        lenient().when(branchAccessService.canAccessCinema(anyLong(), anyLong())).thenReturn(true);
        lenient().when(paymentQueryService.findSuccessfulPaymentMethod(BOOKING_ID))
                .thenReturn(Optional.of(PaymentMethod.VNPAY));
        lenient().when(fnbQueryService.findComboCompositions(any())).thenReturn(Map.of(30L, "1 bắp + 2 nước"));
        lenient().when(bookingDetailService.findBookingDetail(BOOKING_ID))
                .thenAnswer(invocation -> Optional.of(buildBookingDetail()));
    }

    @Test
    void scanReturnsValidForPaidBookingInsideEntryWindow() {
        TicketScanResponse ticketScanResponse = scan(QR_CODE);

        assertEquals(CheckInResultCode.VALID, ticketScanResponse.resultCode());
        assertTrue(ticketScanResponse.canCheckIn());
        assertEquals(2, ticketScanResponse.pendingTicketCount());
        assertEquals(2, ticketScanResponse.booking().tickets().size());
    }

    @Test
    void scanAcceptsBookingCodeTypedByHand() {
        TicketScanResponse ticketScanResponse = scan(" bk62072b12c606 ");

        assertEquals(CheckInResultCode.VALID, ticketScanResponse.resultCode());
    }

    @Test
    void scanIncludesPaymentMethodBookingStatusAndComboComposition() {
        TicketScanResponse ticketScanResponse = scan(QR_CODE);

        assertEquals(PaymentMethod.VNPAY, ticketScanResponse.booking().paymentMethod());
        assertEquals(BookingStatus.PAID, ticketScanResponse.booking().bookingStatus());
        assertEquals("1 bắp + 2 nước", ticketScanResponse.booking().fnbLines().get(0).composition());
        assertEquals(BigDecimal.valueOf(289000), ticketScanResponse.booking().finalAmount());
    }

    @Test
    void scanReturnsNotFoundForUnknownCode() {
        TicketScanResponse ticketScanResponse = scan("QRUNKNOWN");

        assertEquals(CheckInResultCode.NOT_FOUND, ticketScanResponse.resultCode());
        assertFalse(ticketScanResponse.canCheckIn());
        assertNull(ticketScanResponse.booking());
    }

    @Test
    void scanRegistersUnknownCodeWithGuard() {
        scan("QRUNKNOWN");

        verify(scanGuard).assertScanAllowed(STAFF_USER_ID, CINEMA_ID, "CHECK_IN");
        verify(scanGuard).registerUnknownCode(STAFF_USER_ID, CINEMA_ID, "CHECK_IN", "QRUNKNOWN");
    }

    @Test
    void scanDoesNotRegisterUnknownCodeForKnownBooking() {
        scan(QR_CODE);

        verify(scanGuard, never()).registerUnknownCode(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void scanStopsWhenGuardRejectsTooManyScans() {
        doThrow(new AppException("Quét quá nhiều", HttpStatus.TOO_MANY_REQUESTS, "SCAN_RATE_LIMITED"))
                .when(scanGuard).assertScanAllowed(STAFF_USER_ID, CINEMA_ID, "CHECK_IN");

        AppException appException = assertThrows(AppException.class, () -> scan(QR_CODE));

        assertEquals("SCAN_RATE_LIMITED", appException.getErrorCode());
        verify(ticketScanLogRepository, never()).save(any());
    }

    @Test
    void undoWritesAuditLog() {
        tickets.forEach(ticket -> ticket.setCheckedIn(true));

        ticketCheckInService.undoCheckIn(STAFF_USER_ID, new TicketUndoRequest(QR_CODE, CINEMA_ID, List.of(2L)));

        verify(auditLogService).record(
                eq(STAFF_USER_ID), eq(CINEMA_ID), eq(AuditAction.UNDO_CHECK_IN), eq("BOOKING"), any(), any());
    }

    @Test
    void scanReturnsNotYetTimeMoreThanOneHourBeforeShowtime() {
        clockInstant = SHOWTIME_START.minusSeconds(61 * 60);

        TicketScanResponse ticketScanResponse = scan(QR_CODE);

        assertEquals(CheckInResultCode.NOT_YET_TIME, ticketScanResponse.resultCode());
        assertFalse(ticketScanResponse.canCheckIn());
    }

    @Test
    void scanReturnsValidExactlyOneHourBeforeShowtime() {
        clockInstant = SHOWTIME_START.minusSeconds(60 * 60);

        assertEquals(CheckInResultCode.VALID, scan(QR_CODE).resultCode());
    }

    @Test
    void scanReturnsExpiredAfterShowtimeEnded() {
        clockInstant = SHOWTIME_END.plusSeconds(1);

        TicketScanResponse ticketScanResponse = scan(QR_CODE);

        assertEquals(CheckInResultCode.EXPIRED, ticketScanResponse.resultCode());
        assertFalse(ticketScanResponse.canCheckIn());
    }

    @Test
    void scanReturnsWrongBranchWhenWorkingCinemaDiffers() {
        TicketScanResponse ticketScanResponse = ticketCheckInService.scan(
                STAFF_USER_ID, new TicketScanRequest(QR_CODE, 2L));

        assertEquals(CheckInResultCode.WRONG_BRANCH, ticketScanResponse.resultCode());
        assertFalse(ticketScanResponse.canCheckIn());
    }

    @Test
    void scanReturnsWrongBranchWhenStaffHasNoAccessToBookingCinema() {
        when(branchAccessService.canAccessCinema(STAFF_USER_ID, CINEMA_ID)).thenReturn(false);

        assertEquals(CheckInResultCode.WRONG_BRANCH, scan(QR_CODE).resultCode());
    }

    @Test
    void scanRejectsStaffWhoCannotWorkAtRequestedCinema() {
        doThrow(new AppException("Không có quyền", HttpStatus.FORBIDDEN))
                .when(branchAccessService).assertCanAccessCinema(STAFF_USER_ID, CINEMA_ID);

        assertThrows(AppException.class, () -> scan(QR_CODE));
        verify(ticketScanLogRepository, never()).save(any());
    }

    @Test
    void scanReturnsNotPaidForPendingBooking() {
        booking.setStatus(BookingStatus.PENDING);

        assertEquals(CheckInResultCode.NOT_PAID, scan(QR_CODE).resultCode());
    }

    @Test
    void scanReturnsCancelledForCancelledBooking() {
        booking.setStatus(BookingStatus.CANCELLED);

        assertEquals(CheckInResultCode.CANCELLED, scan(QR_CODE).resultCode());
    }

    @Test
    void scanReturnsRefundedForRefundRequiredBooking() {
        booking.setStatus(BookingStatus.REFUND_REQUIRED);

        assertEquals(CheckInResultCode.REFUNDED, scan(QR_CODE).resultCode());
    }

    @Test
    void scanReturnsAlreadyUsedWhenEveryTicketCheckedIn() {
        tickets.forEach(ticket -> ticket.setCheckedIn(true));

        TicketScanResponse ticketScanResponse = scan(QR_CODE);

        assertEquals(CheckInResultCode.ALREADY_USED, ticketScanResponse.resultCode());
        assertFalse(ticketScanResponse.canCheckIn());
    }

    @Test
    void scanStaysValidWhenOnlySomeTicketsCheckedIn() {
        tickets.get(0).setCheckedIn(true);

        TicketScanResponse ticketScanResponse = scan(QR_CODE);

        assertEquals(CheckInResultCode.VALID, ticketScanResponse.resultCode());
        assertEquals(1, ticketScanResponse.pendingTicketCount());
    }

    @Test
    void scanWritesLookupLogWithStaffAndCinema() {
        scan(QR_CODE);

        ArgumentCaptor<TicketScanLog> logCaptor = ArgumentCaptor.forClass(TicketScanLog.class);
        verify(ticketScanLogRepository).save(logCaptor.capture());
        assertEquals(TicketScanAction.LOOKUP, logCaptor.getValue().getAction());
        assertEquals(STAFF_USER_ID, logCaptor.getValue().getStaffUserId());
        assertEquals(CINEMA_ID, logCaptor.getValue().getCinemaId());
        assertEquals(CheckInResultCode.VALID, logCaptor.getValue().getResultCode());
    }

    @Test
    void checkInMarksAllUncheckedTicketsWhenNoSeatSelected() {
        TicketScanResponse ticketScanResponse = ticketCheckInService.checkIn(
                STAFF_USER_ID, new TicketCheckInRequest(QR_CODE, CINEMA_ID, null));

        assertEquals(CheckInResultCode.CHECKED_IN, ticketScanResponse.resultCode());
        assertTrue(tickets.stream().allMatch(Ticket::isCheckedIn));
        assertTrue(tickets.stream().allMatch(ticket -> ticket.getCheckedInAt() != null));
        verify(ticketRepository).saveAll(any());
    }

    @Test
    void checkInMarksOnlySelectedTickets() {
        ticketCheckInService.checkIn(STAFF_USER_ID, new TicketCheckInRequest(QR_CODE, CINEMA_ID, List.of(1L)));

        assertTrue(tickets.get(0).isCheckedIn());
        assertFalse(tickets.get(1).isCheckedIn());
    }

    @Test
    void checkInRejectsTicketOfAnotherBooking() {
        assertThrows(AppException.class, () -> ticketCheckInService.checkIn(
                STAFF_USER_ID, new TicketCheckInRequest(QR_CODE, CINEMA_ID, List.of(999L))));
        assertFalse(tickets.get(0).isCheckedIn());
    }

    @Test
    void secondCheckInOfSameTicketDoesNotChangeAnything() {
        ticketCheckInService.checkIn(STAFF_USER_ID, new TicketCheckInRequest(QR_CODE, CINEMA_ID, List.of(1L)));
        Instant firstCheckedInAt = tickets.get(0).getCheckedInAt();
        clockInstant = NOW.plusSeconds(30);

        TicketScanResponse ticketScanResponse = ticketCheckInService.checkIn(
                STAFF_USER_ID, new TicketCheckInRequest(QR_CODE, CINEMA_ID, List.of(1L)));

        assertEquals(CheckInResultCode.ALREADY_USED, ticketScanResponse.resultCode());
        assertEquals(firstCheckedInAt, tickets.get(0).getCheckedInAt());
    }

    @Test
    void checkInDoesNothingWhenBookingNotPaid() {
        booking.setStatus(BookingStatus.PENDING);

        TicketScanResponse ticketScanResponse = ticketCheckInService.checkIn(
                STAFF_USER_ID, new TicketCheckInRequest(QR_CODE, CINEMA_ID, null));

        assertEquals(CheckInResultCode.NOT_PAID, ticketScanResponse.resultCode());
        assertFalse(tickets.get(0).isCheckedIn());
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void checkInLocksBookingRowBeforeChangingTickets() {
        ticketCheckInService.checkIn(STAFF_USER_ID, new TicketCheckInRequest(QR_CODE, CINEMA_ID, null));

        verify(bookingRepository).findByIdForUpdate(BOOKING_ID);
    }

    @Test
    void undoClearsCheckedInStateOfSelectedTickets() {
        tickets.forEach(ticket -> {
            ticket.setCheckedIn(true);
            ticket.setCheckedInAt(NOW);
        });

        TicketScanResponse ticketScanResponse = ticketCheckInService.undoCheckIn(
                STAFF_USER_ID, new TicketUndoRequest(QR_CODE, CINEMA_ID, List.of(2L)));

        assertEquals(CheckInResultCode.UNDONE, ticketScanResponse.resultCode());
        assertTrue(tickets.get(0).isCheckedIn());
        assertFalse(tickets.get(1).isCheckedIn());
        assertNull(tickets.get(1).getCheckedInAt());
    }

    @Test
    void undoRejectsBookingOfAnotherCinema() {
        assertThrows(AppException.class, () -> ticketCheckInService.undoCheckIn(
                STAFF_USER_ID, new TicketUndoRequest(QR_CODE, 2L, List.of(1L))));
    }

    private TicketScanResponse scan(String code) {
        return ticketCheckInService.scan(STAFF_USER_ID, new TicketScanRequest(code, CINEMA_ID));
    }

    @Test
    void historyOnlyListsConfirmedCheckInsAndUndos() {
        when(ticketScanLogRepository.findByCinemaIdAndResultCodeInOrderByIdDesc(
                CINEMA_ID, List.of(CheckInResultCode.CHECKED_IN, CheckInResultCode.UNDONE), PageRequest.of(0, 50)))
                .thenReturn(List.of());

        assertTrue(ticketCheckInService.findHistory(STAFF_USER_ID, CINEMA_ID).isEmpty());
        verify(ticketScanLogRepository, never()).findByCinemaIdOrderByIdDesc(anyLong(), any());
    }

    private Ticket buildTicket(Long ticketId, Long seatId) {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setBookingId(BOOKING_ID);
        ticket.setSeatId(seatId);
        ticket.setTicketCode("TK" + ticketId);
        ticket.setPrice(BigDecimal.valueOf(90000));
        return ticket;
    }

    private BookingDetailResponse buildBookingDetail() {
        BookingShowtimeResponse showtime = new BookingShowtimeResponse(
                20L, SHOWTIME_START, SHOWTIME_END, 3L, "dua-con-cua-thoi-tiet", "Đứa con của thời tiết",
                null, AgeRating.P, 120, CINEMA_ID, "Star Cineplex Bitexco", 5L, "Phòng 1", ScreenType.SCREEN_2D);
        List<BookingSeatResponse> seats = tickets.stream()
                .map(ticket -> new BookingSeatResponse(
                        ticket.getSeatId(), "A" + (ticket.getSeatId() - 10), "Ghế thường", "STANDARD",
                        ticket.getPrice()))
                .toList();
        List<BookingTicketResponse> bookingTickets = tickets.stream()
                .map(ticket -> new BookingTicketResponse(
                        ticket.getId(), ticket.getTicketCode(), ticket.getSeatId(),
                        "A" + (ticket.getSeatId() - 10), ticket.getPrice(), ticket.isCheckedIn(),
                        ticket.getCheckedInAt()))
                .toList();
        return new BookingDetailResponse(
                BOOKING_ID, booking.getBookingCode(), booking.getStatus(), NOW, null, NOW, showtime, seats,
                List.of(new BookingFnbLineResponse(
                        30L, null, "Combo đôi", null, 1, BigDecimal.valueOf(109000), BigDecimal.valueOf(109000), 0, null)),
                BigDecimal.valueOf(180000), BigDecimal.valueOf(109000), BigDecimal.ZERO,
                BigDecimal.valueOf(289000), BigDecimal.valueOf(289000), bookingTickets, null);
    }
}
