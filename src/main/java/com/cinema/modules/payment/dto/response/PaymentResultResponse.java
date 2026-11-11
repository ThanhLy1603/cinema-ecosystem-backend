package com.cinema.modules.payment.dto.response;

import com.cinema.modules.booking.enums.BookingStatus;
import com.cinema.modules.payment.enums.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResultResponse(
        String transactionRef,
        PaymentStatus paymentStatus,
        Long bookingId,
        BookingStatus bookingStatus,
        BigDecimal amount) {
}
