package com.cinema.modules.staff.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.staff.dto.request.CloseShiftRequest;
import com.cinema.modules.staff.dto.request.OpenShiftRequest;
import com.cinema.modules.staff.dto.response.ShiftReportResponse;
import com.cinema.modules.staff.dto.response.ShiftResponse;
import com.cinema.modules.staff.dto.response.StaffOverviewResponse;
import com.cinema.modules.staff.service.interfaces.IStaffOverviewService;
import com.cinema.modules.staff.service.interfaces.IStaffShiftService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffShiftController {

    private static final int MY_SHIFTS_LIMIT = 20;

    private final IStaffShiftService staffShiftService;
    private final IStaffOverviewService staffOverviewService;

    public StaffShiftController(IStaffShiftService staffShiftService, IStaffOverviewService staffOverviewService) {
        this.staffShiftService = staffShiftService;
        this.staffOverviewService = staffOverviewService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<StaffOverviewResponse>> findOverview(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @RequestParam Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(
                staffOverviewService.findOverview(authenticatedUser.userId(), cinemaId)));
    }

    @PostMapping("/shifts/open")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> openShift(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody OpenShiftRequest openShiftRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                staffShiftService.openShift(authenticatedUser.userId(), openShiftRequest)));
    }

    @GetMapping("/shifts/current")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> findCurrentShift(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(staffShiftService.findCurrentShift(authenticatedUser.userId())));
    }

    @PostMapping("/shifts/close")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> closeShift(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CloseShiftRequest closeShiftRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                staffShiftService.closeShift(authenticatedUser.userId(), closeShiftRequest)));
    }

    @GetMapping("/shifts")
    public ResponseEntity<ApiResponse<List<ShiftResponse>>> findMyShifts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(
                staffShiftService.findMyShifts(authenticatedUser.userId(), MY_SHIFTS_LIMIT)));
    }

    @GetMapping("/shifts/{shiftId}")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> findShiftReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long shiftId) {
        return ResponseEntity.ok(ApiResponse.success(
                staffShiftService.findShiftReport(authenticatedUser.userId(), shiftId)));
    }
}
