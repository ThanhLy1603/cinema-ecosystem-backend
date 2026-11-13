package com.cinema.modules.payment.dto.response;

import com.cinema.modules.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CounterFnbReceiptResponse(
        String orderCode,
        Long cinemaId,
        String cinemaName,
        List<CounterFnbReceiptLineResponse> lines,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        BigDecimal cashReceived,
        BigDecimal changeAmount,
        String soldByStaffName,
        Instant paidAt) {
}
