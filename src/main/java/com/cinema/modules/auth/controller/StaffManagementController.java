package com.cinema.modules.auth.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.dto.request.AssignBranchesRequest;
import com.cinema.modules.auth.dto.request.CreateStaffRequest;
import com.cinema.modules.auth.dto.request.UpdateStaffRequest;
import com.cinema.modules.auth.dto.response.StaffResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.service.interfaces.IStaffManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class StaffManagementController {

    private final IStaffManagementService staffManagementService;

    public StaffManagementController(IStaffManagementService staffManagementService) {
        this.staffManagementService = staffManagementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateStaffRequest createStaffRequest,
            HttpServletRequest httpServletRequest) {
        StaffResponse staffResponse = staffManagementService.createStaff(
                authenticatedUser.userId(), createStaffRequest, httpServletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản nhân viên thành công", staffResponse));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<StaffResponse>> getStaff(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(staffManagementService.getStaff(userId)));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateStaffRequest updateStaffRequest,
            HttpServletRequest httpServletRequest) {
        StaffResponse staffResponse = staffManagementService.updateStaff(
                authenticatedUser.userId(), userId, updateStaffRequest, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ nhân viên thành công", staffResponse));
    }

    @PutMapping("/{userId}/branches")
    public ResponseEntity<ApiResponse<StaffResponse>> assignBranches(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @Valid @RequestBody AssignBranchesRequest assignBranchesRequest,
            HttpServletRequest httpServletRequest) {
        StaffResponse staffResponse = staffManagementService.assignBranches(
                authenticatedUser.userId(), userId, assignBranchesRequest, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Gán chi nhánh thành công", staffResponse));
    }
}
