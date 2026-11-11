package com.cinema.modules.movie.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class MovieUsageChecker {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public MovieUsageChecker(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public long countUpcomingShowtimesByMovie(Long movieId, Instant now) {
        Long showtimeCount = namedParameterJdbcTemplate.queryForObject(
                "select count(*) from showtimes showtime "
                        + "where showtime.movie_id = :movieId and showtime.end_time > :now and showtime.status = 'ACTIVE'",
                new MapSqlParameterSource("movieId", movieId).addValue("now", Timestamp.from(now)),
                Long.class);
        return showtimeCount == null ? 0 : showtimeCount;
    }

    public long countShowtimesWithBookingsByMovie(Long movieId, Instant now) {
        Long showtimeCount = namedParameterJdbcTemplate.queryForObject(
                "select count(distinct booked_showtime.showtime_id) from ("
                        + "select seat_reservation.showtime_id from seat_reservations seat_reservation "
                        + "join showtimes showtime on showtime.id = seat_reservation.showtime_id "
                        + "where showtime.movie_id = :movieId "
                        + "and not (seat_reservation.status = 'HELD' and seat_reservation.expires_at < :now) "
                        + "union all "
                        + "select ticket.showtime_id from tickets ticket "
                        + "join showtimes showtime on showtime.id = ticket.showtime_id "
                        + "where showtime.movie_id = :movieId"
                        + ") booked_showtime",
                new MapSqlParameterSource("movieId", movieId).addValue("now", Timestamp.from(now)),
                Long.class);
        return showtimeCount == null ? 0 : showtimeCount;
    }
}
