package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.movie.enums.AgeRating;
import com.cinema.modules.showtime.enums.ShowtimeStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowtimeResponse(
        Long id,
        Long movieId,
        String movieTitle,
        AgeRating rated,
        int movieDurationMinutes,
        Long cinemaId,
        Long hallId,
        String hallName,
        ScreenType screenType,
        Instant startTime,
        Instant endTime,
        Instant occupiedUntil,
        BigDecimal basePrice,
        ShowtimeStatus status,
        boolean hasBookings,
        boolean editable,
        boolean cancellable) {
}
