package com.cinema.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID Token không được để trống")
        @Size(max = 4096, message = "Google ID Token không hợp lệ")
        String idToken) {
}
