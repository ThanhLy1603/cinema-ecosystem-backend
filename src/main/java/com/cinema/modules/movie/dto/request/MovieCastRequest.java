package com.cinema.modules.movie.dto.request;

import com.cinema.modules.movie.enums.CastRoleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MovieCastRequest(
        @NotNull(message = "Vui lòng chọn nghệ sĩ")
        Long personId,

        @NotNull(message = "Vui lòng chọn vai trò")
        CastRoleType roleType,

        @Size(max = 150, message = "Tên nhân vật tối đa 150 ký tự")
        String characterName) {
}
