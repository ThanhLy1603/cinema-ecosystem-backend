package com.cinema.modules.auth.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignBranchesRequest(
        @NotEmpty(message = "Phải gán ít nhất một chi nhánh")
        @Size(max = 50, message = "Tối đa 50 chi nhánh")
        List<@NotNull(message = "Mã chi nhánh không hợp lệ") @Positive(message = "Mã chi nhánh không hợp lệ") Long> cinemaIds,

        @Positive(message = "Mã chi nhánh chính không hợp lệ")
        Long primaryCinemaId) {
}
