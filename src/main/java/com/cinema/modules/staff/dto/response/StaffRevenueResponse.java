package com.cinema.modules.staff.dto.response;

import java.math.BigDecimal;

public record StaffRevenueResponse(Long staffUserId, String staffName, long orderCount, BigDecimal amount) {
}
