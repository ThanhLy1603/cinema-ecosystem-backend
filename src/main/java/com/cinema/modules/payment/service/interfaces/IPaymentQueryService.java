package com.cinema.modules.payment.service.interfaces;

import com.cinema.modules.payment.enums.PaymentMethod;

import java.util.Optional;

public interface IPaymentQueryService {

    Optional<PaymentMethod> findSuccessfulPaymentMethod(Long bookingId);
}
