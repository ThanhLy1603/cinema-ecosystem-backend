package com.cinema.modules.auth.dto.response;

import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.StaffProfile;
import com.cinema.modules.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        String status,
        boolean emailVerified,
        boolean hasPassword,
        List<String> roles,
        String tier,
        int loyaltyPoints,
        Instant lastLoginAt,
        Instant createdAt,
        String employeeCode,
        String position,
        String staffStatus,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate hiredAt,
        List<BranchAssignmentResponse> branches) {

    public static AdminUserResponse from(
            User user, StaffProfile staffProfile, List<BranchAssignmentResponse> branchAssignmentResponses) {
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getStatus().name(),
                user.isEmailVerified(),
                user.hasPassword(),
                roleCodes,
                user.getTier().name(),
                user.getLoyaltyPoints(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                staffProfile == null ? null : staffProfile.getEmployeeCode(),
                staffProfile == null ? null : staffProfile.getPosition().name(),
                staffProfile == null ? null : staffProfile.getStatus().name(),
                staffProfile == null ? null : staffProfile.getHiredAt(),
                branchAssignmentResponses);
    }
}
