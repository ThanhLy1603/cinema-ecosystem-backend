package com.cinema.modules.auth.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserRolesRequest(
        @NotEmpty(message = "Phải chọn một role")
        @Size(min = 1, max = 1, message = "Mỗi tài khoản chỉ có đúng một role")
        List<@Pattern(
                regexp = "^ROLE_(CUSTOMER|STAFF|MANAGER|ADMIN)$",
                message = "Role không hợp lệ") String> roles) {
}
