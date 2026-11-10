package com.cinema.modules.fnb.dto.response;

import com.cinema.modules.fnb.enums.FnbStatus;

import java.math.BigDecimal;
import java.util.List;

public record FnbComboResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        FnbStatus status,
        List<FnbComboItemResponse> items,
        BigDecimal itemsTotalPrice,
        boolean orderable,
        boolean deleted) {
}
