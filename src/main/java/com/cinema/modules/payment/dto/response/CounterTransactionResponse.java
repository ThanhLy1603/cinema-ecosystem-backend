package com.cinema.modules.payment.dto.response;

import com.cinema.modules.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record CounterTransactionResponse(
        String type,
        String code,
        PaymentMethod paymentMethod,
        BigDecimal amount,
        Instant occurredAt) {
}
