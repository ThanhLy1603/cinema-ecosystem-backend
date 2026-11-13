package com.cinema.modules.booking.dto.response;

import java.time.Instant;

public record FnbDeliveryLogResponse(
        Long id, String bookingCode, String productName, int quantity, String staffName, Instant createdAt) {
}
