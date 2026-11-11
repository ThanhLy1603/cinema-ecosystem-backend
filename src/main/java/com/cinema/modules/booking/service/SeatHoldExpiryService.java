package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.entity.Booking;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        List<SeatReservation> releasedSeatReservations = new ArrayList<>();
        releasedSeatReservations.addAll(expireOverdueBookings(now));
        releasedSeatReservations.addAll(deleteOrphanExpiredHolds(now));
        if (releasedSeatReservations.isEmpty()) {
            return 0;
        }
        Map<Long, List<SeatStateChange>> changesByShowtimeId = releasedSeatReservations.stream()
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
        return releasedSeatReservations.size();
    }

    private List<SeatReservation> expireOverdueBookings(Instant now) {
        List<Booking> overdueBookings = bookingRepository.findOverdueForUpdate(BookingStatus.PENDING, now);
        List<SeatReservation> releasedSeatReservations = new ArrayList<>();
        for (Booking booking : overdueBookings) {
            List<SeatReservation> heldSeatReservations =
                    seatReservationRepository.findByBookingIdOrderBySeatId(booking.getId()).stream()
                            .filter(seatReservation -> seatReservation.getStatus() == SeatReservationStatus.HELD)
                            .toList();
            if (!heldSeatReservations.isEmpty()) {
                seatReservationRepository.deleteHeldByIds(
                        heldSeatReservations.stream().map(SeatReservation::getId).toList(),
                        SeatReservationStatus.HELD);
                releasedSeatReservations.addAll(heldSeatReservations);
            }
            booking.setStatus(BookingStatus.EXPIRED);
        }
        bookingRepository.saveAll(overdueBookings);
        return releasedSeatReservations;
    }

    private List<SeatReservation> deleteOrphanExpiredHolds(Instant now) {
        List<SeatReservation> orphanSeatReservations =
                seatReservationRepository.findByStatusAndExpiresAtBefore(SeatReservationStatus.HELD, now);
        if (orphanSeatReservations.isEmpty()) {
            return List.of();
        }
        List<Long> orphanIds = orphanSeatReservations.stream().map(SeatReservation::getId).toList();
        seatReservationRepository.deleteHeldByIds(orphanIds, SeatReservationStatus.HELD);
        Set<Long> survivingIds = new HashSet<>();
        seatReservationRepository.findAllById(orphanIds)
                .forEach(survivingSeatReservation -> survivingIds.add(survivingSeatReservation.getId()));
        return orphanSeatReservations.stream()
                .filter(seatReservation -> !survivingIds.contains(seatReservation.getId()))
                .toList();
    }
}
