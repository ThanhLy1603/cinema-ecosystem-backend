package com.cinema.modules.showtime.dto.response;

import com.cinema.modules.showtime.enums.BulkItemStatus;

import java.time.Instant;
import java.util.List;

public record BulkShowtimeItemResponse(
        Long hallId,
        String hallName,
        Long movieId,
        Instant startTime,
        Instant endTime,
        BulkItemStatus status,
        String reason,
        List<ShowtimeConflictResponse> conflicts,
        Long showtimeId) {
}
