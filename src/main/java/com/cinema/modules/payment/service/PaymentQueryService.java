package com.cinema.modules.payment.service;

import com.cinema.modules.payment.entity.Payment;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.enums.PaymentStatus;
import com.cinema.modules.payment.repository.PaymentRepository;
import com.cinema.modules.payment.service.interfaces.IPaymentQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentQueryService implements IPaymentQueryService {

    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<PaymentMethod> findSuccessfulPaymentMethod(Long bookingId) {
        return paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCESS)
                .map(Payment::getPaymentMethod);
    }
}
