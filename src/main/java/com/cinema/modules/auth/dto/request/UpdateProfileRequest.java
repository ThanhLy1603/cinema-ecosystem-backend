package com.cinema.modules.auth.dto.request;

import com.cinema.modules.auth.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullName,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @Past(message = "Ngày sinh phải ở quá khứ")
        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 500, message = "Đường dẫn ảnh đại diện tối đa 500 ký tự")
        @Pattern(regexp = "^https://\\S+$", message = "Ảnh đại diện phải là đường dẫn https")
        String avatarUrl) {
}
