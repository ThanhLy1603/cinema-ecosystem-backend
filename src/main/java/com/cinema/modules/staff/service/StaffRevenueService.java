package com.cinema.modules.staff.service;

import com.cinema.modules.auth.dto.response.UserContactResponse;
import com.cinema.modules.auth.scope.CinemaScope;
import com.cinema.modules.auth.scope.CinemaScopeResolver;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.payment.dto.response.RevenueChannelResponse;
import com.cinema.modules.payment.dto.response.RevenueSummaryResponse;
import com.cinema.modules.payment.service.interfaces.IRevenueReportService;
import com.cinema.modules.staff.dto.response.RevenueReportResponse;
import com.cinema.modules.staff.dto.response.ShiftResponse;
import com.cinema.modules.staff.dto.response.StaffRevenueResponse;
import com.cinema.modules.staff.service.interfaces.IStaffRevenueService;
import com.cinema.modules.staff.service.interfaces.IStaffShiftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class StaffRevenueService implements IStaffRevenueService {

    private static final int MAX_SHIFTS_IN_REPORT = 100;

    private final IRevenueReportService revenueReportService;
    private final IStaffShiftService staffShiftService;
    private final CinemaScopeResolver cinemaScopeResolver;
    private final IUserQueryService userQueryService;

    public StaffRevenueService(
            IRevenueReportService revenueReportService,
            IStaffShiftService staffShiftService,
            CinemaScopeResolver cinemaScopeResolver,
            IUserQueryService userQueryService) {
        this.revenueReportService = revenueReportService;
        this.staffShiftService = staffShiftService;
        this.cinemaScopeResolver = cinemaScopeResolver;
        this.userQueryService = userQueryService;
    }

    @Transactional(readOnly = true)
    @Override
    public RevenueReportResponse findRevenueReport(Long managerUserId, Long cinemaId, Instant from, Instant to) {
        CinemaScope cinemaScope = cinemaScopeResolver.resolve(managerUserId, cinemaId);
        RevenueSummaryResponse revenueSummaryResponse = revenueReportService.summarize(
                cinemaScope.allCinemas() ? null : cinemaScope.cinemaIds(), from, to);
        List<StaffRevenueResponse> staffRevenueResponses = revenueSummaryResponse.staff().stream()
                .map(revenueStaffResponse -> new StaffRevenueResponse(
                        revenueStaffResponse.staffUserId(),
                        userQueryService.findContact(revenueStaffResponse.staffUserId())
                                .map(UserContactResponse::fullName)
                                .orElse("Nhân viên #" + revenueStaffResponse.staffUserId()),
                        revenueStaffResponse.orderCount(),
                        revenueStaffResponse.amount()))
                .toList();
        List<ShiftResponse> shiftResponses = staffShiftService
                .findShifts(managerUserId, cinemaId, null, from, to, 0, MAX_SHIFTS_IN_REPORT)
                .content();
        BigDecimal totalAmount = revenueSummaryResponse.channels().stream()
                .map(RevenueChannelResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalOrders = revenueSummaryResponse.channels().stream()
                .mapToLong(RevenueChannelResponse::orderCount)
                .sum();
        return new RevenueReportResponse(
                totalAmount, totalOrders, revenueSummaryResponse.channels(), staffRevenueResponses, shiftResponses);
    }
}
