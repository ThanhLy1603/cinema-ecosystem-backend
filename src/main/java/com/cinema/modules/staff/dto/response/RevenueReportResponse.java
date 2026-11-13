package com.cinema.modules.staff.dto.response;

import com.cinema.modules.payment.dto.response.RevenueChannelResponse;

import java.math.BigDecimal;
import java.util.List;

public record RevenueReportResponse(
        BigDecimal totalAmount,
        long totalOrders,
        List<RevenueChannelResponse> channels,
        List<StaffRevenueResponse> staff,
        List<ShiftResponse> shifts) {
}
