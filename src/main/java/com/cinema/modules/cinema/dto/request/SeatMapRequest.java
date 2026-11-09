package com.cinema.modules.cinema.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SeatMapRequest(
        @NotNull(message = "Thiếu phiên bản sơ đồ ghế")
        Long version,

        @NotNull(message = "Thiếu danh sách ghế")
        @Size(max = 800, message = "Một phòng chiếu tối đa 800 ghế")
        List<@Valid SeatCellRequest> seats) {
}
