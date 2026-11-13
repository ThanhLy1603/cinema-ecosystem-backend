package com.cinema.modules.booking.dto.response;

import java.time.Instant;
import java.util.List;

public record TicketQueueResponse(Instant serverTime, List<TicketQueueShowtimeGroupResponse> showtimes) {
}
