package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.showtime.enums.ShowtimeConflictType;

import java.time.Instant;

public record ShowtimeConflictResponse(
        Long showtimeId,
        Long movieId,
        String movieTitle,
        Instant startTime,
        Instant endTime,
        Instant occupiedUntil,
        ShowtimeConflictType conflictType) {
}
