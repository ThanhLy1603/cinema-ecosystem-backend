package com.cinema.modules.payment.dto.response;

import java.time.Instant;

public record PaymentUrlResponse(String paymentUrl, String transactionRef, Instant expiresAt) {
}
