package com.cinema.modules.fnb.dto.response;

import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;

import java.math.BigDecimal;

public record FnbComboItemResponse(
        Long itemId,
        String itemName,
        FnbCategory category,
        BigDecimal unitPrice,
        int quantity,
        FnbStatus itemStatus,
        boolean itemDeleted) {
}
