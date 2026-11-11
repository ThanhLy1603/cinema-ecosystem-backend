package com.cinema.modules.booking.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.dto.response.PayableBookingResponse;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.entity.Ticket;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import com.cinema.modules.booking.enums.SettlementOutcome;
import com.cinema.modules.booking.event.BookingPaidEvent;
import com.cinema.modules.booking.repository.BookingFnbDetailRepository;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingSettlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final Long BOOKING_ID = 100L;
    private static final Long USER_ID = 5L;
    private static final Long SHOWTIME_ID = 20L;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private BookingFnbDetailRepository bookingFnbDetailRepository;

    @Mock
    private SeatGapChecker seatGapChecker;

    @Mock
    private ISeatStatusBroadcaster seatStatusBroadcaster;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private BookingSettlementService bookingSettlementService;
    private Booking booking;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        bookingSettlementService = new BookingSettlementService(
                bookingRepository,
                seatReservationRepository,
                ticketRepository,
                bookingFnbDetailRepository,
                seatGapChecker,
                new TicketCodeGenerator(),
                seatStatusBroadcaster,
                applicationEventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setBookingCode("BK123");
        booking.setUserId(USER_ID);
        booking.setShowtimeId(SHOWTIME_ID);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(NOW.plusSeconds(300));
        booking.setTotalAmount(new BigDecimal("215000"));
        booking.setFinalAmount(new BigDecimal("215000"));
        lenient().when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private SeatReservation reservation(Long seatId, SeatReservationStatus status, String price) {
        SeatReservation seatReservation = new SeatReservation();
        seatReservation.setShowtimeId(SHOWTIME_ID);
        seatReservation.setSeatId(seatId);
        seatReservation.setBookingId(BOOKING_ID);
        seatReservation.setUserId(USER_ID);
        seatReservation.setStatus(status);
        seatReservation.setPrice(price == null ? null : new BigDecimal(price));
        return seatReservation;
    }

    @Test
    void pendingBookingWithHeldSeatsBecomesPaidWithOneUniqueTicketPerSeatAndPublishesTheEvent() {
        when(seatReservationRepository.findByBookingIdOrderBySeatId(BOOKING_ID)).thenReturn(List.of(
                reservation(11L, SeatReservationStatus.HELD, "90000"), reservation(12L, SeatReservationStatus.HELD, "100000")));

        SettlementOutcome settlementOutcome = bookingSettlementService.settlePaidBooking(BOOKING_ID);

        assertEquals(SettlementOutcome.PAID, settlementOutcome);
        assertEquals(BookingStatus.PAID, booking.getStatus());
        assertTrue(booking.getQrCode().startsWith("QR"));
        assertEquals(26, booking.getQrCode().length());
        verify(seatReservationRepository)
                .markBookedByBookingId(BOOKING_ID, SeatReservationStatus.HELD, SeatReservationStatus.BOOKED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> ticketsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        List<Ticket> tickets = ticketsCaptor.getValue();
        assertEquals(2, tickets.size());
        assertEquals(new BigDecimal("90000"), tickets.get(0).getPrice());
        assertEquals(new BigDecimal("100000"), tickets.get(1).getPrice());
        assertEquals(SHOWTIME_ID, tickets.get(0).getShowtimeId());
        assertTrue(tickets.get(0).getTicketCode().startsWith("TK"));
        assertTrue(!tickets.get(0).getTicketCode().equals(tickets.get(1).getTicketCode()));
        verify(applicationEventPublisher)
                .publishEvent(new BookingPaidEvent(BOOKING_ID, USER_ID, "BK123", new BigDecimal("215000")));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(seatStatusBroadcaster).broadcast(SHOWTIME_ID, List.of(
                new SeatStateChange(11L, SeatAvailability.SOLD), new SeatStateChange(12L, SeatAvailability.SOLD)));
    }

    @Test
    void settlingAnAlreadyPaidBookingDoesNothingSoNoSecondSetOfTicketsOrPointsIsIssued() {
        booking.setStatus(BookingStatus.PAID);

        SettlementOutcome settlementOutcome = bookingSettlementService.settlePaidBooking(BOOKING_ID);

        assertEquals(SettlementOutcome.ALREADY_PAID, settlementOutcome);
        assertEquals(null, booking.getQrCode());
        verify(ticketRepository, never()).saveAll(any());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
        verify(seatReservationRepository, never()).markBookedByBookingId(any(), any(), any());
    }

    @Test
    void paymentAfterTheBookingExpiredAndSeatsWereFreedNeedsARefundAndIssuesNoTicket() {
        booking.setStatus(BookingStatus.EXPIRED);

        SettlementOutcome settlementOutcome = bookingSettlementService.settlePaidBooking(BOOKING_ID);

        assertEquals(SettlementOutcome.REFUND_REQUIRED, settlementOutcome);
        assertEquals(BookingStatus.REFUND_REQUIRED, booking.getStatus());
        verify(ticketRepository, never()).saveAll(any());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void pendingBookingWhoseSeatsDisappearedNeedsARefund() {
        when(seatReservationRepository.findByBookingIdOrderBySeatId(BOOKING_ID)).thenReturn(List.of());

        assertEquals(SettlementOutcome.REFUND_REQUIRED, bookingSettlementService.settlePaidBooking(BOOKING_ID));
        assertEquals(BookingStatus.REFUND_REQUIRED, booking.getStatus());
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void refundRequiredStaysRefundRequiredOnRepeatedCallbacks() {
        booking.setStatus(BookingStatus.REFUND_REQUIRED);

        assertEquals(SettlementOutcome.REFUND_REQUIRED, bookingSettlementService.settlePaidBooking(BOOKING_ID));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void legacyReservationsWithoutAPriceShareTheTicketAmountEvenly() {
        booking.setTotalAmount(new BigDecimal("215000"));
        when(bookingFnbDetailRepository.sumAmountByBookingId(BOOKING_ID)).thenReturn(new BigDecimal("35000"));
        when(seatReservationRepository.findByBookingIdOrderBySeatId(BOOKING_ID)).thenReturn(List.of(
                reservation(11L, SeatReservationStatus.HELD, null), reservation(12L, SeatReservationStatus.HELD, null)));

        bookingSettlementService.settlePaidBooking(BOOKING_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> ticketsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        assertEquals(new BigDecimal("90000.00"), ticketsCaptor.getValue().get(0).getPrice());
    }

    @Test
    void payableBookingRequiresAnUnexpiredPendingBookingWithHeldSeats() {
        when(bookingRepository.findByUserIdAndStatusForUpdate(USER_ID, BookingStatus.PENDING))
                .thenReturn(Optional.of(booking));
        when(seatReservationRepository.findByBookingIdOrderBySeatId(BOOKING_ID))
                .thenReturn(List.of(reservation(11L, SeatReservationStatus.HELD, "90000")));

        PayableBookingResponse payableBookingResponse = bookingSettlementService.requirePayableBooking(USER_ID);

        assertEquals("BK123", payableBookingResponse.bookingCode());
        assertEquals(new BigDecimal("215000"), payableBookingResponse.finalAmount());

        booking.setExpiresAt(NOW.minusSeconds(1));
        AppException expiredException =
                assertThrows(AppException.class, () -> bookingSettlementService.requirePayableBooking(USER_ID));
        assertEquals("NO_PENDING_BOOKING", expiredException.getErrorCode());
    }
}
