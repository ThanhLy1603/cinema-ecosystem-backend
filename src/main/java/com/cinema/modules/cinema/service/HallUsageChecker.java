package com.cinema.modules.cinema.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class HallUsageChecker {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public HallUsageChecker(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public boolean hasShowtimes(Long hallId) {
        Long showtimeCount = namedParameterJdbcTemplate.queryForObject(
                "select count(*) from showtimes showtime where showtime.hall_id = :hallId",
                new MapSqlParameterSource("hallId", hallId),
                Long.class);
        return showtimeCount != null && showtimeCount > 0;
    }

    public long countUpcomingShowtimes(Long cinemaId, Instant now) {
        Long showtimeCount = namedParameterJdbcTemplate.queryForObject(
                "select count(*) from showtimes showtime join halls hall on hall.id = showtime.hall_id "
                        + "where hall.cinema_id = :cinemaId and showtime.end_time > :now",
                new MapSqlParameterSource("cinemaId", cinemaId).addValue("now", Timestamp.from(now)),
                Long.class);
        return showtimeCount == null ? 0 : showtimeCount;
    }

    public Set<Long> findSeatIdsInUse(Collection<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(
                "select seat_reservation.seat_id from seat_reservations seat_reservation "
                        + "where seat_reservation.seat_id in (:seatIds) "
                        + "union select ticket.seat_id from tickets ticket where ticket.seat_id in (:seatIds)",
                new MapSqlParameterSource("seatIds", seatIds),
                Long.class));
    }
}
