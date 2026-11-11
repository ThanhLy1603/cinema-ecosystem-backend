package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.entity.Booking;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatHoldExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ISeatStatusBroadcaster seatStatusBroadcaster;

    private SeatHoldExpiryService seatHoldExpiryService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        seatHoldExpiryService = new SeatHoldExpiryService(
                seatReservationRepository, bookingRepository, seatStatusBroadcaster, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void overdueBookingsAreLockedExpiredAndTheirHeldSeatsFreedPerShowtime() {
        Booking firstBooking = booking(100L);
        Booking secondBooking = booking(101L);
        SeatReservation firstSeatReservation = reservation(1L, 100L, 5L, 11L);
        SeatReservation secondSeatReservation = reservation(2L, 100L, 5L, 12L);
        SeatReservation otherShowtimeSeatReservation = reservation(3L, 101L, 6L, 11L);
        when(bookingRepository.findOverdueForUpdate(BookingStatus.PENDING, NOW))
                .thenReturn(List.of(firstBooking, secondBooking));
        when(seatReservationRepository.findByBookingIdOrderBySeatId(100L))
                .thenReturn(List.of(firstSeatReservation, secondSeatReservation));
        when(seatReservationRepository.findByBookingIdOrderBySeatId(101L))
                .thenReturn(List.of(otherShowtimeSeatReservation));
        when(seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, NOW))
                .thenReturn(List.of());

        int releasedCount = seatHoldExpiryService.releaseExpiredHolds();

        assertEquals(3, releasedCount);
        assertEquals(BookingStatus.EXPIRED, firstBooking.getStatus());
        assertEquals(BookingStatus.EXPIRED, secondBooking.getStatus());
        verify(seatReservationRepository).deleteHeldByIds(List.of(1L, 2L), SeatReservationStatus.HELD);
        verify(seatReservationRepository).deleteHeldByIds(List.of(3L), SeatReservationStatus.HELD);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(seatStatusBroadcaster).broadcast(5L, List.of(
                new SeatStateChange(11L, SeatAvailability.AVAILABLE), new SeatStateChange(12L, SeatAvailability.AVAILABLE)));
        verify(seatStatusBroadcaster).broadcast(6L, List.of(new SeatStateChange(11L, SeatAvailability.AVAILABLE)));
    }

    @Test
    void aBookedSeatOfAnOverdueBookingIsNeverDeleted() {
        Booking booking = booking(100L);
        SeatReservation bookedSeatReservation = reservation(1L, 100L, 5L, 11L);
        bookedSeatReservation.setStatus(SeatReservationStatus.BOOKED);
        when(bookingRepository.findOverdueForUpdate(BookingStatus.PENDING, NOW)).thenReturn(List.of(booking));
        when(seatReservationRepository.findByBookingIdOrderBySeatId(100L)).thenReturn(List.of(bookedSeatReservation));
        when(seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, NOW))
                .thenReturn(List.of());

        assertEquals(0, seatHoldExpiryService.releaseExpiredHolds());

        verify(seatReservationRepository, never()).deleteHeldByIds(any(), any());
        verify(seatStatusBroadcaster, never()).broadcast(any(), any());
    }

    @Test
    void orphanExpiredHoldsAreDeletedOnlyWhenTheyStillExistAsHeldAndAreBroadcastOnlyWhenGone() {
        SeatReservation deletedSeatReservation = reservation(7L, null, 5L, 21L);
        SeatReservation survivingSeatReservation = reservation(8L, null, 5L, 22L);
        when(bookingRepository.findOverdueForUpdate(BookingStatus.PENDING, NOW)).thenReturn(List.of());
        when(seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, NOW))
                .thenReturn(List.of(deletedSeatReservation, survivingSeatReservation));
        when(seatReservationRepository.findAllById(List.of(7L, 8L))).thenReturn(List.of(survivingSeatReservation));

        int releasedCount = seatHoldExpiryService.releaseExpiredHolds();

        assertEquals(1, releasedCount);
        verify(seatReservationRepository).deleteHeldByIds(List.of(7L, 8L), SeatReservationStatus.HELD);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(seatStatusBroadcaster).broadcast(5L, List.of(new SeatStateChange(21L, SeatAvailability.AVAILABLE)));
    }

    @Test
    void nothingExpiredMeansNothingBroadcast() {
        when(bookingRepository.findOverdueForUpdate(BookingStatus.PENDING, NOW)).thenReturn(List.of());
        when(seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, NOW))
                .thenReturn(List.of());

        assertEquals(0, seatHoldExpiryService.releaseExpiredHolds());

        verify(seatStatusBroadcaster, never()).broadcast(any(), any());
    }

    private Booking booking(Long bookingId) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(NOW.minusSeconds(1));
        return booking;
    }

    private SeatReservation reservation(Long id, Long bookingId, Long showtimeId, Long seatId) {
        SeatReservation seatReservation = new SeatReservation();
        seatReservation.setId(id);
        seatReservation.setBookingId(bookingId);
        seatReservation.setShowtimeId(showtimeId);
        seatReservation.setSeatId(seatId);
        seatReservation.setUserId(1L);
        seatReservation.setStatus(SeatReservationStatus.HELD);
        seatReservation.setExpiresAt(NOW.minusSeconds(1));
        return seatReservation;
    }
}
