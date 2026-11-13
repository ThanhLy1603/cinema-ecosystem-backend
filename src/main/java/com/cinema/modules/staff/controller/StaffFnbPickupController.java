package com.cinema.modules.staff.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.booking.dto.request.FnbPickupDeliverRequest;
import com.cinema.modules.booking.dto.request.FnbPickupScanRequest;
import com.cinema.modules.booking.dto.response.FnbDeliveryLogResponse;
import com.cinema.modules.booking.dto.response.FnbPickupQueueResponse;
import com.cinema.modules.booking.dto.response.FnbPickupScanResponse;
import com.cinema.modules.booking.service.interfaces.IFnbPickupService;
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
@RequestMapping("/api/v1/staff/fnb-pickup")
public class StaffFnbPickupController {

    private final IFnbPickupService fnbPickupService;

    public StaffFnbPickupController(IFnbPickupService fnbPickupService) {
        this.fnbPickupService = fnbPickupService;
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<FnbPickupScanResponse>> scan(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody FnbPickupScanRequest fnbPickupScanRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                fnbPickupService.scan(authenticatedUser.userId(), fnbPickupScanRequest)));
    }

    @PostMapping("/deliver")
    public ResponseEntity<ApiResponse<FnbPickupScanResponse>> deliver(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody FnbPickupDeliverRequest fnbPickupDeliverRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                fnbPickupService.deliver(authenticatedUser.userId(), fnbPickupDeliverRequest)));
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<FnbPickupQueueResponse>> findQueue(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(
                fnbPickupService.findQueue(authenticatedUser.userId(), cinemaId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<FnbDeliveryLogResponse>>> findHistory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(
                fnbPickupService.findHistory(authenticatedUser.userId(), cinemaId)));
    }
}
