package com.cinema.modules.payment.dto.response;

import com.cinema.modules.booking.dto.response.BookingDetailResponse;
import com.cinema.modules.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record CounterReceiptResponse(
        BookingDetailResponse booking,
        PaymentMethod paymentMethod,
        BigDecimal cashReceived,
        BigDecimal changeAmount,
        String soldByStaffName,
        String customerName,
        Instant paidAt) {
}
