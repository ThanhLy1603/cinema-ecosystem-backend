package com.cinema.modules.showtime.dto.response;

import java.util.List;

public record BulkShowtimeResponse(
        boolean dryRun,
        int totalCount,
        int acceptedCount,
        int conflictCount,
        int invalidCount,
        List<BulkShowtimeItemResponse> items) {
}
