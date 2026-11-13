package com.cinema.modules.payment.service.interfaces;

import com.cinema.modules.payment.dto.request.CounterCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterReceiptResponse;

public interface ICounterPaymentService {

    CounterReceiptResponse checkout(Long staffUserId, CounterCheckoutRequest counterCheckoutRequest);
}
