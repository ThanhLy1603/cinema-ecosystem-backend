package com.cinema.modules.payment.service.interfaces;

import com.cinema.modules.payment.dto.request.CounterFnbCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptResponse;

public interface ICounterFnbSaleService {

    CounterFnbReceiptResponse checkout(Long staffUserId, CounterFnbCheckoutRequest counterFnbCheckoutRequest);

    CounterFnbReceiptResponse findReceipt(Long staffUserId, String orderCode);
}
