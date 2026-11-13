package com.cinema.modules.staff.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.booking.dto.request.TicketCheckInRequest;
import com.cinema.modules.booking.dto.request.TicketScanRequest;
import com.cinema.modules.booking.dto.request.TicketUndoRequest;
import com.cinema.modules.booking.dto.response.TicketQueueResponse;
import com.cinema.modules.booking.dto.response.TicketScanLogResponse;
import com.cinema.modules.booking.dto.response.TicketScanResponse;
import com.cinema.modules.booking.service.interfaces.ITicketCheckInQueueService;
import com.cinema.modules.booking.service.interfaces.ITicketCheckInService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/check-in")
public class StaffCheckInController {

    private final ITicketCheckInService ticketCheckInService;
    private final ITicketCheckInQueueService ticketCheckInQueueService;

    public StaffCheckInController(
            ITicketCheckInService ticketCheckInService, ITicketCheckInQueueService ticketCheckInQueueService) {
        this.ticketCheckInService = ticketCheckInService;
        this.ticketCheckInQueueService = ticketCheckInQueueService;
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<TicketScanResponse>> scan(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody TicketScanRequest ticketScanRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketCheckInService.scan(authenticatedUser.userId(), ticketScanRequest)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<TicketScanResponse>> checkIn(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody TicketCheckInRequest ticketCheckInRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketCheckInService.checkIn(authenticatedUser.userId(), ticketCheckInRequest)));
    }

    @PostMapping("/undo")
    public ResponseEntity<ApiResponse<TicketScanResponse>> undoCheckIn(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody TicketUndoRequest ticketUndoRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketCheckInService.undoCheckIn(authenticatedUser.userId(), ticketUndoRequest)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TicketScanLogResponse>>> findHistory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketCheckInService.findHistory(authenticatedUser.userId(), cinemaId)));
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<TicketQueueResponse>> findQueue(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketCheckInQueueService.findQueue(authenticatedUser.userId(), cinemaId)));
    }
}
