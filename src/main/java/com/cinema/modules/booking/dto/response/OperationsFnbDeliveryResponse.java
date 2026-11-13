package com.cinema.modules.booking.dto.response;

import java.time.Instant;

public record OperationsFnbDeliveryResponse(
        Long id,
        String bookingCode,
        String productName,
        int quantity,
        Long staffUserId,
        String staffName,
        Long cinemaId,
        String cinemaName,
        Instant createdAt) {
}
