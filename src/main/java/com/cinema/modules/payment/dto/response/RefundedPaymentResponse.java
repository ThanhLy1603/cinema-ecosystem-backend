package com.cinema.modules.payment.dto.response;

import com.cinema.modules.payment.enums.PaymentMethod;

import java.math.BigDecimal;

public record RefundedPaymentResponse(PaymentMethod paymentMethod, BigDecimal amount) {
}
