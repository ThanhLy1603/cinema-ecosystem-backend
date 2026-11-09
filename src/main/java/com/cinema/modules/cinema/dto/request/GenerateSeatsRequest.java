package com.cinema.modules.cinema.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GenerateSeatsRequest(
        @Min(value = 1, message = "Số hàng tối thiểu là 1")
        @Max(value = 26, message = "Số hàng tối đa là 26 (A đến Z)")
        int rowCount,

        @Min(value = 1, message = "Số cột tối thiểu là 1")
        @Max(value = 40, message = "Số cột tối đa là 40")
        int columnCount,

        @NotBlank(message = "Vui lòng chọn loại ghế mặc định")
        String seatTypeCode) {
}
