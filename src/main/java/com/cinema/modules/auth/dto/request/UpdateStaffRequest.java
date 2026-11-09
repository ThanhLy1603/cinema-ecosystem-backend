package com.cinema.modules.auth.dto.request;

import com.cinema.modules.auth.enums.StaffPosition;
import com.cinema.modules.auth.enums.StaffStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateStaffRequest(
        @NotBlank(message = "Role không được để trống")
        @Pattern(regexp = "^ROLE_(STAFF|MANAGER)$", message = "Role chỉ được là ROLE_STAFF hoặc ROLE_MANAGER")
        String role,

        @NotBlank(message = "Mã nhân viên không được để trống")
        @Size(max = 50, message = "Mã nhân viên tối đa 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã nhân viên chỉ gồm chữ, số, gạch ngang và gạch dưới")
        String employeeCode,

        @NotNull(message = "Chức danh không được để trống")
        StaffPosition position,

        @NotNull(message = "Trạng thái nhân viên không được để trống")
        StaffStatus status,

        LocalDate hiredAt) {
}
