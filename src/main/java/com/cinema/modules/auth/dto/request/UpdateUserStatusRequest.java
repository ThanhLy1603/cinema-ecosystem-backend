package com.cinema.modules.auth.dto.request;

import com.cinema.modules.auth.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        UserStatus status) {
}
