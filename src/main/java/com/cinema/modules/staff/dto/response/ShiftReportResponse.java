package com.cinema.modules.staff.dto.response;

import com.cinema.modules.payment.dto.response.CounterMethodTotalResponse;
import com.cinema.modules.payment.dto.response.CounterTransactionResponse;

import java.math.BigDecimal;
import java.util.List;

public record ShiftReportResponse(
        ShiftResponse shift,
        List<CounterMethodTotalResponse> methodTotals,
        BigDecimal cashSales,
        List<CounterTransactionResponse> transactions) {
}
