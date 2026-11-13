package com.cinema.modules.staff.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.common.time.VietnamTime;
import com.cinema.modules.audit.dto.response.AuditLogResponse;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.interfaces.IAuditLogService;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.booking.dto.request.BookingExceptionActionRequest;
import com.cinema.modules.booking.dto.response.BookingExceptionResponse;
import com.cinema.modules.booking.dto.response.OperationsFnbDeliveryResponse;
import com.cinema.modules.booking.dto.response.OperationsScanLogResponse;
import com.cinema.modules.booking.enums.CheckInResultCode;
import com.cinema.modules.booking.service.interfaces.IBookingExceptionService;
import com.cinema.modules.booking.service.interfaces.IOperationsHistoryService;
import com.cinema.modules.staff.dto.response.RevenueReportResponse;
import com.cinema.modules.staff.dto.response.ShiftReportResponse;
import com.cinema.modules.staff.dto.response.ShiftResponse;
import com.cinema.modules.staff.service.interfaces.IStaffRevenueService;
import com.cinema.modules.staff.service.interfaces.IStaffShiftService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/operations")
public class AdminOperationsController {

    private final IOperationsHistoryService operationsHistoryService;
    private final IBookingExceptionService bookingExceptionService;
    private final IAuditLogService auditLogService;
    private final IStaffRevenueService staffRevenueService;
    private final IStaffShiftService staffShiftService;

    public AdminOperationsController(
            IOperationsHistoryService operationsHistoryService,
            IBookingExceptionService bookingExceptionService,
            IAuditLogService auditLogService,
            IStaffRevenueService staffRevenueService,
            IStaffShiftService staffShiftService) {
        this.operationsHistoryService = operationsHistoryService;
        this.bookingExceptionService = bookingExceptionService;
        this.auditLogService = auditLogService;
        this.staffRevenueService = staffRevenueService;
        this.staffShiftService = staffShiftService;
    }

    @GetMapping("/scan-logs")
    public ResponseEntity<ApiResponse<PageResponse<OperationsScanLogResponse>>> findScanLogs(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Long staffUserId,
            @RequestParam(required = false) CheckInResultCode resultCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(operationsHistoryService.findScanLogs(
                authenticatedUser.userId(), cinemaId, staffUserId, resultCode, startOf(from), endOf(to), page, size)));
    }

    @GetMapping("/fnb-deliveries")
    public ResponseEntity<ApiResponse<PageResponse<OperationsFnbDeliveryResponse>>> findFnbDeliveries(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Long staffUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(operationsHistoryService.findFnbDeliveries(
                authenticatedUser.userId(), cinemaId, staffUserId, startOf(from), endOf(to), page, size)));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> findAuditLogs(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.findLogs(
                authenticatedUser.userId(), cinemaId, actorUserId, action, startOf(from), endOf(to), page, size)));
    }

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<RevenueReportResponse>> findRevenueReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(staffRevenueService.findRevenueReport(
                authenticatedUser.userId(), cinemaId, startOf(from), endOf(to))));
    }

    @GetMapping("/shifts")
    public ResponseEntity<ApiResponse<PageResponse<ShiftResponse>>> findShifts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Long staffUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(staffShiftService.findShifts(
                authenticatedUser.userId(), cinemaId, staffUserId, startOf(from), endOf(to), page, size)));
    }

    @GetMapping("/shifts/{shiftId}")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> findShiftReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long shiftId) {
        return ResponseEntity.ok(ApiResponse.success(
                staffShiftService.findShiftReport(authenticatedUser.userId(), shiftId)));
    }

    @GetMapping("/bookings/lookup")
    public ResponseEntity<ApiResponse<BookingExceptionResponse>> lookupBooking(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @RequestParam String code) {
        return ResponseEntity.ok(ApiResponse.success(bookingExceptionService.lookup(authenticatedUser.userId(), code)));
    }

    @PostMapping("/bookings/{bookingCode}/reissue")
    public ResponseEntity<ApiResponse<BookingExceptionResponse>> reissueTicket(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String bookingCode,
            @Valid @RequestBody BookingExceptionActionRequest bookingExceptionActionRequest) {
        return ResponseEntity.ok(ApiResponse.success(bookingExceptionService.reissueTicket(
                authenticatedUser.userId(), bookingCode, bookingExceptionActionRequest.reason().trim())));
    }

    @PostMapping("/bookings/{bookingCode}/refund")
    public ResponseEntity<ApiResponse<BookingExceptionResponse>> refundBooking(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String bookingCode,
            @Valid @RequestBody BookingExceptionActionRequest bookingExceptionActionRequest) {
        return ResponseEntity.ok(ApiResponse.success(bookingExceptionService.refundBooking(
                authenticatedUser.userId(), bookingCode, bookingExceptionActionRequest.reason().trim())));
    }

    private Instant startOf(LocalDate date) {
        return date == null ? null : VietnamTime.startOfDay(date);
    }

    private Instant endOf(LocalDate date) {
        return date == null ? null : VietnamTime.startOfNextDay(date);
    }
}
