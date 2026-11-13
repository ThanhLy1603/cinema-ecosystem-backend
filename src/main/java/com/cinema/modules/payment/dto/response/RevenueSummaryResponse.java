package com.cinema.modules.payment.dto.response;

import java.util.List;

public record RevenueSummaryResponse(
        List<RevenueChannelResponse> channels,
        List<RevenueStaffResponse> staff) {
}
