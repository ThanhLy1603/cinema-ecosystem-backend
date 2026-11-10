package com.cinema.modules.booking.dto.response;

import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;

import java.time.Instant;
import java.util.List;

public record ShowtimeSeatMapResponse(
        Long showtimeId,
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
        ScreenType screenType,
        Instant startTime,
        Instant endTime,
        Instant serverTime,
        List<String> rows,
        int columns,
        List<ShowtimeSeatResponse> seats) {
}
