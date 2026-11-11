package com.cinema.modules.fnb.dto.response;

import java.math.BigDecimal;

public record FnbProductInfoResponse(
        Long id,
        String name,
        String imageUrl,
        BigDecimal price,
        boolean orderable) {
}
