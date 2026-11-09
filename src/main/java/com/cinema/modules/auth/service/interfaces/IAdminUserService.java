package com.cinema.modules.auth.service.interfaces;

import com.cinema.common.response.PageResponse;
import com.cinema.modules.auth.dto.request.UpdateUserInfoRequest;
import com.cinema.modules.auth.dto.request.UpdateUserRolesRequest;
import com.cinema.modules.auth.dto.request.UpdateUserStatusRequest;
import com.cinema.modules.auth.dto.response.AdminUserResponse;
import com.cinema.modules.auth.dto.response.AuditLogResponse;
import com.cinema.modules.auth.enums.UserStatus;

public interface IAdminUserService {

    PageResponse<AdminUserResponse> searchUsers(Long actorUserId, String keyword, String roleCode, UserStatus status, Long cinemaId, int page, int size);

    AdminUserResponse getUser(Long actorUserId, Long targetUserId);

    AdminUserResponse updateUserInfo(Long actorUserId, Long targetUserId, UpdateUserInfoRequest updateUserInfoRequest, String ipAddress);

    AdminUserResponse updateStatus(Long actorUserId, Long targetUserId, UpdateUserStatusRequest updateUserStatusRequest, String ipAddress);

    AdminUserResponse updateRoles(Long actorUserId, Long targetUserId, UpdateUserRolesRequest updateUserRolesRequest, String ipAddress);

    void deleteUser(Long actorUserId, Long targetUserId, String ipAddress);

    PageResponse<AuditLogResponse> getAuditLogs(Long actorUserId, Long targetUserId, int page, int size);
}
