package com.cinema.modules.staff.service.interfaces;

import com.cinema.modules.staff.dto.response.RevenueReportResponse;

import java.time.Instant;

public interface IStaffRevenueService {

    RevenueReportResponse findRevenueReport(Long managerUserId, Long cinemaId, Instant from, Instant to);
}
