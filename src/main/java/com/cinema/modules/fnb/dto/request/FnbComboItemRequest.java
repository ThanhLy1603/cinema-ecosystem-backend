package com.cinema.modules.fnb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FnbComboItemRequest(
        @NotNull(message = "Vui lòng chọn món")
        Long itemId,

        @NotNull(message = "Vui lòng nhập số lượng")
        @Min(value = 1, message = "Số lượng tối thiểu 1")
        @Max(value = 20, message = "Số lượng tối đa 20")
        Integer quantity) {
}
