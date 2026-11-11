package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.cinema.enums.ScreenType;

import java.math.BigDecimal;
import java.time.Instant;

public record BookableShowtimeResponse(
        Long id,
        Long hallId,
        String hallName,
        ScreenType screenType,
        Instant startTime,
        Instant endTime,
        BigDecimal basePrice,
        int totalSeats,
        int availableSeats,
        boolean soldOut,
        boolean started) {
}
