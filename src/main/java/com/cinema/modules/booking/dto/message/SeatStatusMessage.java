package com.cinema.modules.booking.dto.message;

import java.time.Instant;
import java.util.List;

public record SeatStatusMessage(Long showtimeId, List<SeatStateChange> changes, Instant occurredAt) {
}
