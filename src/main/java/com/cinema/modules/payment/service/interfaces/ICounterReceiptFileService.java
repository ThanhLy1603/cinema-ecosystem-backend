package com.cinema.modules.payment.service.interfaces;

import com.cinema.modules.payment.dto.response.CounterReceiptFileResponse;

public interface ICounterReceiptFileService {

    CounterReceiptFileResponse saveReceiptFile(Long staffUserId, String bookingCode);

    CounterReceiptFileResponse saveFnbReceiptFile(Long staffUserId, String orderCode);
}
