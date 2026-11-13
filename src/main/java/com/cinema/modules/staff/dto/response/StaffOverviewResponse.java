package com.cinema.modules.staff.dto.response;

import java.time.Instant;
import java.util.List;

public record StaffOverviewResponse(
        Instant serverTime,
        List<ShowtimeOverviewResponse> showtimes,
        int pendingFnbBookingCount,
        int pendingCheckInTicketCount,
        ShiftReportResponse currentShift) {
}
