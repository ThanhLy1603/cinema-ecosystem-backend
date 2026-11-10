package com.cinema.modules.showtime.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class ShowtimeBookingChecker {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ShowtimeBookingChecker(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Set<Long> findShowtimeIdsWithBookings(Collection<Long> showtimeIds, Instant now) {
        if (showtimeIds == null || showtimeIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(
                "select ticket.showtime_id from tickets ticket where ticket.showtime_id in (:showtimeIds) "
                        + "union "
                        + "select seat_reservation.showtime_id from seat_reservations seat_reservation "
                        + "where seat_reservation.showtime_id in (:showtimeIds) "
                        + "and not (seat_reservation.status = 'HELD' and seat_reservation.expires_at < :now)",
                new MapSqlParameterSource("showtimeIds", showtimeIds).addValue("now", Timestamp.from(now)),
                Long.class));
    }
}
