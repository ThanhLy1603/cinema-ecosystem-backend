package com.cinema.modules.booking.dto.response;

import java.math.BigDecimal;

public record ScanFnbLineResponse(
        String name,
        String imageUrl,
        String composition,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {
}
