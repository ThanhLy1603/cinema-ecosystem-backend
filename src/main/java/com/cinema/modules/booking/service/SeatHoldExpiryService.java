package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.entity.SeatReservation;
import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.booking.enums.SeatAvailability;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import com.cinema.modules.booking.repository.BookingRepository;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.service.interfaces.ISeatHoldExpiryService;
import com.cinema.modules.booking.service.interfaces.ISeatStatusBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SeatHoldExpiryService implements ISeatHoldExpiryService {

    private final SeatReservationRepository seatReservationRepository;
    private final BookingRepository bookingRepository;
    private final ISeatStatusBroadcaster seatStatusBroadcaster;
    private final Clock clock;

    public SeatHoldExpiryService(
            SeatReservationRepository seatReservationRepository,
            BookingRepository bookingRepository,
            ISeatStatusBroadcaster seatStatusBroadcaster,
            Clock clock) {
        this.seatReservationRepository = seatReservationRepository;
        this.bookingRepository = bookingRepository;
        this.seatStatusBroadcaster = seatStatusBroadcaster;
        this.clock = clock;
    }

    @Transactional
    @Override
    public int releaseExpiredHolds() {
        Instant now = Instant.now(clock);
        List<SeatReservation> expiredSeatReservations =
                seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, now);
        bookingRepository.expireOverdueBookings(BookingStatus.PENDING, BookingStatus.EXPIRED, now);
        if (expiredSeatReservations.isEmpty()) {
            return 0;
        }
        seatReservationRepository.deleteAllInBatch(expiredSeatReservations);
        Map<Long, List<SeatStateChange>> changesByShowtimeId = expiredSeatReservations.stream()
                .collect(Collectors.groupingBy(
                        SeatReservation::getShowtimeId,
                        Collectors.mapping(
                                seatReservation -> new SeatStateChange(seatReservation.getSeatId(), SeatAvailability.AVAILABLE),
                                Collectors.toList())));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                changesByShowtimeId.forEach(seatStatusBroadcaster::broadcast);
            }
        });
        return expiredSeatReservations.size();
    }
}
