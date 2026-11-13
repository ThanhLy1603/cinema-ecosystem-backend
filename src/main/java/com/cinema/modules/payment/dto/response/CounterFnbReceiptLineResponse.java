package com.cinema.modules.payment.dto.response;

import java.math.BigDecimal;

public record CounterFnbReceiptLineResponse(
        Long comboId,
        Long itemId,
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {
}
