package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record FnbPickupLineResponse(
        Long detailId,
        String name,
        String imageUrl,
        String composition,
        int quantity,
        int deliveredQuantity,
        int remainingQuantity,
        BigDecimal unitPrice,
        Instant deliveredAt,
        String deliveredByStaffName) {
}
