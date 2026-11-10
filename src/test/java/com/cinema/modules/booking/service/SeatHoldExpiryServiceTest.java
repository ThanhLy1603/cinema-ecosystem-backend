package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.message.SeatStateChange;
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
    void expiredHoldsAreDeletedAndEachShowtimeIsToldAboutItsFreedSeats() {
        SeatReservation firstSeatReservation = reservation(5L, 11L);
        SeatReservation secondSeatReservation = reservation(5L, 12L);
        SeatReservation otherShowtimeSeatReservation = reservation(6L, 11L);
        when(seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, NOW))
                .thenReturn(List.of(firstSeatReservation, secondSeatReservation, otherShowtimeSeatReservation));

        int releasedCount = seatHoldExpiryService.releaseExpiredHolds();

        assertEquals(3, releasedCount);
        verify(bookingRepository).expireOverdueBookings(BookingStatus.PENDING, BookingStatus.EXPIRED, NOW);
        verify(seatReservationRepository)
                .deleteAllInBatch(List.of(firstSeatReservation, secondSeatReservation, otherShowtimeSeatReservation));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(seatStatusBroadcaster).broadcast(5L, List.of(
                new SeatStateChange(11L, SeatAvailability.AVAILABLE), new SeatStateChange(12L, SeatAvailability.AVAILABLE)));
        verify(seatStatusBroadcaster).broadcast(6L, List.of(new SeatStateChange(11L, SeatAvailability.AVAILABLE)));
    }

    @Test
    void nothingExpiredMeansNothingBroadcast() {
        when(seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, NOW))
                .thenReturn(List.of());

        assertEquals(0, seatHoldExpiryService.releaseExpiredHolds());

        verify(seatStatusBroadcaster, never()).broadcast(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private SeatReservation reservation(Long showtimeId, Long seatId) {
        SeatReservation seatReservation = new SeatReservation();
        seatReservation.setShowtimeId(showtimeId);
        seatReservation.setSeatId(seatId);
        seatReservation.setUserId(1L);
        seatReservation.setStatus(SeatReservationStatus.HELD);
        seatReservation.setExpiresAt(NOW.minusSeconds(1));
        return seatReservation;
    }
}
