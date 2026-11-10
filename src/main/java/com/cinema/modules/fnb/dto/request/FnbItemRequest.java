package com.cinema.modules.fnb.dto.request;

import com.cinema.modules.fnb.enums.FnbCategory;
import com.cinema.modules.fnb.enums.FnbStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FnbItemRequest(
        @NotBlank(message = "Vui lòng nhập tên món")
        @Size(max = 150, message = "Tên món tối đa 150 ký tự")
        String name,

        @NotNull(message = "Vui lòng chọn nhóm món")
        FnbCategory category,

        @NotNull(message = "Vui lòng nhập giá")
        @DecimalMin(value = "0", message = "Giá không được âm")
        @DecimalMax(value = "10000000", message = "Giá tối đa 10.000.000 VNĐ")
        @Digits(integer = 8, fraction = 2, message = "Giá không hợp lệ")
        BigDecimal price,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        @Pattern(regexp = "^(https://\\S+)?$", message = "Ảnh phải là đường dẫn bắt đầu bằng https://")
        String imageUrl,

        @NotNull(message = "Vui lòng chọn trạng thái")
        FnbStatus status) {
}
