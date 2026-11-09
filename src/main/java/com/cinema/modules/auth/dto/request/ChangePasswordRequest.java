package com.cinema.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "Mật khẩu phải gồm cả chữ và số")
        String newPassword) {
}
