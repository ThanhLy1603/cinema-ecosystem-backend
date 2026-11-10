package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.showtime.enums.ShowtimeStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowtimeInfoResponse(
        Long id,
        Long movieId,
        Long hallId,
        Instant startTime,
        Instant endTime,
        BigDecimal basePrice,
        ShowtimeStatus status) {
}
