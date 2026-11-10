package com.cinema.modules.booking.service;

import com.cinema.modules.booking.enums.SeatAvailability;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class SeatOccupancyReader {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public SeatOccupancyReader(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Map<Long, SeatAvailability> findOccupiedSeats(Long showtimeId, Instant now) {
        Map<Long, SeatAvailability> occupiedSeats = new HashMap<>();
        namedParameterJdbcTemplate.query(
                "select seat_reservation.seat_id, seat_reservation.status as seat_state from seat_reservations seat_reservation "
                        + "where seat_reservation.showtime_id = :showtimeId "
                        + "and not (seat_reservation.status = 'HELD' and seat_reservation.expires_at < :now) "
                        + "union all "
                        + "select ticket.seat_id, 'BOOKED' as seat_state from tickets ticket "
                        + "where ticket.showtime_id = :showtimeId",
                new MapSqlParameterSource("showtimeId", showtimeId).addValue("now", Timestamp.from(now)),
                resultSet -> {
                    SeatAvailability seatAvailability =
                            "HELD".equals(resultSet.getString("seat_state")) ? SeatAvailability.HELD : SeatAvailability.SOLD;
                    occupiedSeats.merge(resultSet.getLong("seat_id"), seatAvailability, SeatOccupancyReader::strongest);
                });
        return occupiedSeats;
    }

    private static SeatAvailability strongest(SeatAvailability firstSeatAvailability, SeatAvailability secondSeatAvailability) {
        return firstSeatAvailability == SeatAvailability.SOLD || secondSeatAvailability == SeatAvailability.SOLD
                ? SeatAvailability.SOLD
                : SeatAvailability.HELD;
    }
}
