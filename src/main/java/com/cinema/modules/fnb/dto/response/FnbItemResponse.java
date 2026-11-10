package com.cinema.modules.fnb.dto.response;

import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;

import java.math.BigDecimal;

public record FnbItemResponse(
        Long id,
        String name,
        FnbCategory category,
        BigDecimal price,
        String imageUrl,
        FnbStatus status,
        boolean deleted) {
}
