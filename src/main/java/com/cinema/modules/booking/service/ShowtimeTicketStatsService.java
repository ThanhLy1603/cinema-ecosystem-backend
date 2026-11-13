package com.cinema.modules.booking.service;

import com.cinema.modules.booking.dto.response.ShowtimeTicketStatsResponse;
import com.cinema.modules.booking.enums.SeatReservationStatus;
import com.cinema.modules.booking.repository.SeatReservationRepository;
import com.cinema.modules.booking.repository.TicketRepository;
import com.cinema.modules.booking.service.interfaces.IShowtimeTicketStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class ShowtimeTicketStatsService implements IShowtimeTicketStatsService {

    private final TicketRepository ticketRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final Clock clock;

    public ShowtimeTicketStatsService(
            TicketRepository ticketRepository, SeatReservationRepository seatReservationRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, ShowtimeTicketStatsResponse> countByShowtimes(Collection<Long> showtimeIds) {
        Map<Long, ShowtimeTicketStatsResponse> showtimeTicketStatsResponses = new HashMap<>();
        if (showtimeIds.isEmpty()) {
            return showtimeTicketStatsResponses;
        }
        Map<Long, Long> heldSeatCounts = new HashMap<>();
        for (Object[] row : seatReservationRepository.countActiveHeldByShowtimeIds(
                showtimeIds, SeatReservationStatus.HELD, Instant.now(clock))) {
            heldSeatCounts.put((Long) row[0], (Long) row[1]);
        }
        for (Object[] row : ticketRepository.countByShowtimeIds(showtimeIds)) {
            Long showtimeId = (Long) row[0];
            showtimeTicketStatsResponses.put(showtimeId, new ShowtimeTicketStatsResponse(
                    (Long) row[1], ((Number) row[2]).longValue(), heldSeatCounts.getOrDefault(showtimeId, 0L)));
        }
        heldSeatCounts.forEach((showtimeId, heldSeats) -> showtimeTicketStatsResponses.putIfAbsent(
                showtimeId, new ShowtimeTicketStatsResponse(0, 0, heldSeats)));
        return showtimeTicketStatsResponses;
    }
}
