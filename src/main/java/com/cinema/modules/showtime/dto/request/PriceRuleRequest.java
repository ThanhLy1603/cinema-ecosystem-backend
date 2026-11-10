package com.cinema.modules.showtime.dto.request;

import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.enums.PriceRuleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PriceRuleRequest(
        @NotBlank(message = "Vui lòng nhập tên quy tắc")
        @Size(max = 150, message = "Tên quy tắc tối đa 150 ký tự")
        String ruleName,

        @NotNull(message = "Vui lòng chọn loại phụ thu")
        PriceRuleType ruleType,

        @NotNull(message = "Vui lòng nhập mức phụ thu")
        @DecimalMin(value = "0", message = "Mức phụ thu không được âm")
        @DecimalMax(value = "10000000", message = "Mức phụ thu tối đa 10.000.000 VNĐ")
        @Digits(integer = 8, fraction = 2, message = "Mức phụ thu không hợp lệ")
        BigDecimal surchargeAmount,

        PriceDayType dayType,
        LocalTime timeFrom,
        LocalTime timeTo,
        ScreenType screenType,
        LocalDate validFrom,
        LocalDate validTo) {
}
