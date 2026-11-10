package com.cinema.modules.showtime.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class ShowtimeSeatOccupancyReader {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ShowtimeSeatOccupancyReader(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Map<Long, Integer> countOccupiedSeatsByShowtime(Collection<Long> showtimeIds, Instant now) {
        Map<Long, Integer> occupiedSeatCounts = new HashMap<>();
        if (showtimeIds == null || showtimeIds.isEmpty()) {
            return occupiedSeatCounts;
        }
        namedParameterJdbcTemplate.query(
                "select occupied_seat.showtime_id, count(*) as seat_count from ("
                        + "select seat_reservation.showtime_id, seat_reservation.seat_id from seat_reservations seat_reservation "
                        + "where seat_reservation.showtime_id in (:showtimeIds) "
                        + "and not (seat_reservation.status = 'HELD' and seat_reservation.expires_at < :now) "
                        + "union "
                        + "select ticket.showtime_id, ticket.seat_id from tickets ticket "
                        + "where ticket.showtime_id in (:showtimeIds)"
                        + ") occupied_seat group by occupied_seat.showtime_id",
                new MapSqlParameterSource("showtimeIds", showtimeIds).addValue("now", Timestamp.from(now)),
                resultSet -> {
                    occupiedSeatCounts.put(resultSet.getLong("showtime_id"), resultSet.getInt("seat_count"));
                });
        return occupiedSeatCounts;
    }
}
