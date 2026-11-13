package com.cinema.modules.booking.dto.response;

import java.time.Instant;
import java.util.List;

public record TicketQueueShowtimeGroupResponse(
        Long showtimeId,
        String movieTitle,
        String hallName,
        Instant startTime,
        Instant endTime,
        Instant checkInOpensAt,
        boolean checkInOpen,
        int pendingBookingCount,
        int pendingTicketCount,
        List<TicketQueueBookingResponse> bookings) {
}
