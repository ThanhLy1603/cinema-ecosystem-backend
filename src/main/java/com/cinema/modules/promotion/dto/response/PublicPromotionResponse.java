package com.cinema.modules.promotion.dto.response;

import com.cinema.modules.promotion.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record PublicPromotionResponse(
        Long id,
        String code,
        String title,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        Instant startDate,
        Instant endDate) {
}
