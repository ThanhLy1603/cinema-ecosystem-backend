package com.cinema.modules.booking.dto.response;

import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;

import java.time.Instant;

public record BookingShowtimeResponse(
        Long showtimeId,
        Instant startTime,
        Instant endTime,
        Long movieId,
        String movieSlug,
        String movieTitle,
        String posterUrl,
        AgeRating rated,
        int durationMinutes,
        Long cinemaId,
        String cinemaName,
        Long hallId,
        String hallName,
        ScreenType screenType) {
}
