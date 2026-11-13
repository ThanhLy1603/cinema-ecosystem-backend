package com.cinema.modules.payment.dto.response;

import java.math.BigDecimal;

public record RevenueChannelResponse(
        String channel,
        long orderCount,
        BigDecimal amount) {
}
