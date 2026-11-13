package com.cinema.modules.booking.dto.response;

import java.time.Instant;
import java.util.List;

public record FnbPickupShowtimeGroupResponse(
        Long showtimeId,
        String movieTitle,
        String hallName,
        Instant startTime,
        Instant endTime,
        Instant pickupOpensAt,
        boolean pickupOpen,
        int pendingBookingCount,
        int pendingQuantity,
        List<FnbPickupQueueBookingResponse> bookings) {
}
