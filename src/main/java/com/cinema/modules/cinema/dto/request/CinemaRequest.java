package com.cinema.modules.cinema.dto.request;

import com.cinema.modules.cinema.enums.CinemaStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CinemaRequest(
        @NotBlank(message = "Vui lòng nhập tên rạp")
        @Size(max = 255, message = "Tên rạp tối đa 255 ký tự")
        String name,

        @NotBlank(message = "Vui lòng chọn tỉnh/thành phố")
        @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự")
        String city,

        @NotBlank(message = "Vui lòng nhập địa chỉ")
        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số hotline không hợp lệ (8 đến 15 chữ số)")
        String phone,

        @DecimalMin(value = "-90", message = "Vĩ độ phải nằm trong khoảng -90 đến 90")
        @DecimalMax(value = "90", message = "Vĩ độ phải nằm trong khoảng -90 đến 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180", message = "Kinh độ phải nằm trong khoảng -180 đến 180")
        @DecimalMax(value = "180", message = "Kinh độ phải nằm trong khoảng -180 đến 180")
        BigDecimal longitude,

        @NotNull(message = "Vui lòng chọn trạng thái")
        CinemaStatus status,

        List<Long> facilityIds) {
}
