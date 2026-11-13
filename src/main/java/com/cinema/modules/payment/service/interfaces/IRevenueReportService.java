package com.cinema.modules.payment.service.interfaces;

import com.cinema.modules.payment.dto.response.CounterMethodTotalResponse;
import com.cinema.modules.payment.dto.response.CounterTransactionResponse;
import com.cinema.modules.payment.dto.response.RevenueSummaryResponse;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface IRevenueReportService {

    List<CounterMethodTotalResponse> sumCounterSalesByMethod(Long staffUserId, Instant from, Instant to);

    List<CounterTransactionResponse> findCounterTransactions(Long staffUserId, Instant from, Instant to);

    RevenueSummaryResponse summarize(Collection<Long> cinemaIds, Instant from, Instant to);
}
