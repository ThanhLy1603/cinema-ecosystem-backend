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

    public long countUpcomingShowtimesByHall(Long hallId, Instant now) {
        Long showtimeCount = namedParameterJdbcTemplate.queryForObject(
                "select count(*) from showtimes showtime "
                        + "where showtime.hall_id = :hallId and showtime.end_time > :now and showtime.status = 'ACTIVE'",
                new MapSqlParameterSource("hallId", hallId).addValue("now", Timestamp.from(now)),
                Long.class);
        return showtimeCount == null ? 0 : showtimeCount;
    }

    public long countUpcomingShowtimesByCinema(Long cinemaId, Instant now) {
        Long showtimeCount = namedParameterJdbcTemplate.queryForObject(
                "select count(*) from showtimes showtime join halls hall on hall.id = showtime.hall_id "
                        + "where hall.cinema_id = :cinemaId and showtime.end_time > :now and showtime.status = 'ACTIVE'",
                new MapSqlParameterSource("cinemaId", cinemaId).addValue("now", Timestamp.from(now)),
                Long.class);
        return showtimeCount == null ? 0 : showtimeCount;
    }

    public Set<Long> findSeatIdsWithUpcomingShowtimes(Collection<Long> seatIds, Instant now) {
        if (seatIds == null || seatIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(
                "select seat_reservation.seat_id from seat_reservations seat_reservation "
                        + "join showtimes showtime on showtime.id = seat_reservation.showtime_id "
                        + "where seat_reservation.seat_id in (:seatIds) and showtime.end_time > :now "
                        + "and not (seat_reservation.status = 'HELD' and seat_reservation.expires_at < :now) "
                        + "union "
                        + "select ticket.seat_id from tickets ticket "
                        + "join showtimes showtime on showtime.id = ticket.showtime_id "
                        + "where ticket.seat_id in (:seatIds) and showtime.end_time > :now",
                new MapSqlParameterSource("seatIds", seatIds).addValue("now", Timestamp.from(now)),
                Long.class));
    }
}
