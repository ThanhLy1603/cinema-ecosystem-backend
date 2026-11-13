package com.cinema.modules.booking.dto.response;

import java.time.Instant;
import java.util.List;

public record FnbPickupQueueResponse(Instant serverTime, List<FnbPickupShowtimeGroupResponse> showtimes) {
}
