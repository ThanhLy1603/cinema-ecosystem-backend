package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;

public record BookingFnbLineResponse(
        Long comboId,
        Long itemId,
        String name,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {
}
