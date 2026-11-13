package com.cinema.modules.staff.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.staff.dto.response.StaffContextResponse;
import com.cinema.modules.staff.service.interfaces.IStaffContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/context")
public class StaffContextController {

    private final IStaffContextService staffContextService;

    public StaffContextController(IStaffContextService staffContextService) {
        this.staffContextService = staffContextService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StaffContextResponse>> getContext(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(staffContextService.getContext(authenticatedUser.userId())));
    }
}
