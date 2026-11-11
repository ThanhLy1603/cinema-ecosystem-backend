package com.cinema.modules.cinema.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

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

    public long countShowtimesWithBookingsByHall(Long hallId, Instant now) {
        Long showtimeCount = namedParameterJdbcTemplate.queryForObject(
                "select count(distinct booked_showtime.showtime_id) from ("
                        + "select seat_reservation.showtime_id from seat_reservations seat_reservation "
                        + "join showtimes showtime on showtime.id = seat_reservation.showtime_id "
                        + "where showtime.hall_id = :hallId and showtime.end_time > :now "
                        + "and not (seat_reservation.status = 'HELD' and seat_reservation.expires_at < :now) "
                        + "union all "
                        + "select ticket.showtime_id from tickets ticket "
                        + "join showtimes showtime on showtime.id = ticket.showtime_id "
                        + "where showtime.hall_id = :hallId and showtime.end_time > :now"
                        + ") booked_showtime",
                new MapSqlParameterSource("hallId", hallId).addValue("now", Timestamp.from(now)),
                Long.class);
        return showtimeCount == null ? 0 : showtimeCount;
    }
}
