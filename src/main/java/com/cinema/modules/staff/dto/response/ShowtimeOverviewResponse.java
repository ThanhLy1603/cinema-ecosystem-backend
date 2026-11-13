package com.cinema.modules.staff.dto.response;

import com.cinema.modules.cinema.enums.ScreenType;

import java.time.Instant;

public record ShowtimeOverviewResponse(
        Long showtimeId,
        String movieTitle,
        String hallName,
        ScreenType screenType,
        Instant startTime,
        Instant endTime,
        boolean nowShowing,
        int capacity,
        long soldSeats,
        long heldSeats,
        long availableSeats,
        long checkedInSeats) {
}
