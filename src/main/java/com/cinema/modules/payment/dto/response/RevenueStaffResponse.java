package com.cinema.modules.payment.dto.response;

import java.math.BigDecimal;

public record RevenueStaffResponse(
        Long staffUserId,
        long orderCount,
        BigDecimal amount) {
}
