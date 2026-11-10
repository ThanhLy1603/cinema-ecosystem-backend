package com.cinema.modules.showtime.model;

import java.math.BigDecimal;
import java.time.Instant;

public record BulkShowtimeCandidate(Long movieId, Long hallId, Instant startTime, BigDecimal basePrice) {
}
