package com.cinema.modules.auth.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.dto.request.UpdateUserInfoRequest;
import com.cinema.modules.auth.dto.request.UpdateUserRolesRequest;
import com.cinema.modules.auth.dto.request.UpdateUserStatusRequest;
import com.cinema.modules.auth.dto.response.AdminUserResponse;
import com.cinema.modules.auth.dto.response.AuditLogResponse;
import com.cinema.modules.auth.enums.UserStatus;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.service.interfaces.IAdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminUserController {

    private final IAdminUserService adminUserService;

    public AdminUserController(IAdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> searchUsers(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.searchUsers(
                authenticatedUser.userId(), search, role, status, cinemaId, page, size)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUser(authenticatedUser.userId(), userId)));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserInfo(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserInfoRequest updateUserInfoRequest,
            HttpServletRequest httpServletRequest) {
        AdminUserResponse adminUserResponse = adminUserService.updateUserInfo(
                authenticatedUser.userId(), userId, updateUserInfoRequest, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin người dùng thành công", adminUserResponse));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest updateUserStatusRequest,
            HttpServletRequest httpServletRequest) {
        AdminUserResponse adminUserResponse = adminUserService.updateStatus(
                authenticatedUser.userId(), userId, updateUserStatusRequest, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái tài khoản thành công", adminUserResponse));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateRoles(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRolesRequest updateUserRolesRequest,
            HttpServletRequest httpServletRequest) {
        AdminUserResponse adminUserResponse = adminUserService.updateRoles(
                authenticatedUser.userId(), userId, updateUserRolesRequest, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật role thành công", adminUserResponse));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            HttpServletRequest httpServletRequest) {
        adminUserService.deleteUser(authenticatedUser.userId(), userId, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Xóa tài khoản thành công", null));
    }

    @GetMapping("/{userId}/audit-logs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.getAuditLogs(authenticatedUser.userId(), userId, page, size)));
    }
}
