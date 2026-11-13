package com.cinema.modules.payment.dto.response;

import com.cinema.modules.payment.enums.PaymentMethod;

import java.math.BigDecimal;

public record CounterMethodTotalResponse(
        PaymentMethod paymentMethod,
        long bookingOrders,
        BigDecimal bookingAmount,
        long fnbOrders,
        BigDecimal fnbAmount) {
}
