package com.cinema.modules.auth.dto.response;

import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.StaffProfile;
import com.cinema.modules.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffResponse(
        Long userId,
        String email,
        String fullName,
        String phone,
        String userStatus,
        boolean emailVerified,
        List<String> roles,
        String employeeCode,
        String position,
        String staffStatus,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate hiredAt,
        List<BranchAssignmentResponse> branches) {

    public static StaffResponse from(
            User user, StaffProfile staffProfile, List<BranchAssignmentResponse> branchAssignmentResponses) {
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new StaffResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus().name(),
                user.isEmailVerified(),
                roleCodes,
                staffProfile.getEmployeeCode(),
                staffProfile.getPosition().name(),
                staffProfile.getStatus().name(),
                staffProfile.getHiredAt(),
                branchAssignmentResponses);
    }
}
