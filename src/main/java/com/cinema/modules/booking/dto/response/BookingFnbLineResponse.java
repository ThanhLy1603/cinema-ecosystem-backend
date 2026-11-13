package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingFnbLineResponse(
        Long comboId,
        Long itemId,
        String name,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        int deliveredQuantity,
        Instant deliveredAt) {
}
