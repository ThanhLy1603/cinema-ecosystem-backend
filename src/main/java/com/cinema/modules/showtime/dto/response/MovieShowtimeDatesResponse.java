package com.cinema.modules.showtime.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record MovieShowtimeDatesResponse(
        Long movieId, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") List<LocalDate> dates) {
}
