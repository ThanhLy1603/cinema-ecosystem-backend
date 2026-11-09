package com.cinema.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Token không được để trống")
        @Size(max = 128, message = "Token không hợp lệ")
        String token) {
}
