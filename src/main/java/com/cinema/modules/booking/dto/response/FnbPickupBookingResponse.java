package com.cinema.modules.booking.dto.response;

import java.time.Instant;
import java.util.List;

public record FnbPickupBookingResponse(
        Long bookingId,
        String bookingCode,
        String movieTitle,
        String posterUrl,
        String cinemaName,
        String hallName,
        Instant startTime,
        Instant endTime,
        List<FnbPickupLineResponse> lines,
        int totalQuantity,
        int remainingQuantity) {
}
