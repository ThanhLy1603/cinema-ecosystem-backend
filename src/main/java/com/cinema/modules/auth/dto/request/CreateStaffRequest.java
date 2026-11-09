package com.cinema.modules.auth.dto.request;

import com.cinema.modules.auth.enums.StaffPosition;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateStaffRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "Mật khẩu phải gồm cả chữ và số")
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullName,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @NotBlank(message = "Role không được để trống")
        @Pattern(regexp = "^ROLE_(STAFF|MANAGER)$", message = "Role chỉ được là ROLE_STAFF hoặc ROLE_MANAGER")
        String role,

        @NotBlank(message = "Mã nhân viên không được để trống")
        @Size(max = 50, message = "Mã nhân viên tối đa 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã nhân viên chỉ gồm chữ, số, gạch ngang và gạch dưới")
        String employeeCode,

        @NotNull(message = "Chức danh không được để trống")
        StaffPosition position,

        LocalDate hiredAt,

        @NotEmpty(message = "Phải gán ít nhất một chi nhánh")
        @Size(max = 50, message = "Tối đa 50 chi nhánh")
        List<@NotNull(message = "Mã chi nhánh không hợp lệ") @Positive(message = "Mã chi nhánh không hợp lệ") Long> cinemaIds,

        @Positive(message = "Mã chi nhánh chính không hợp lệ")
        Long primaryCinemaId) {
}
