package com.cinema.modules.booking.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.booking.dto.response.ShowtimeSeatMapResponse;
import com.cinema.modules.booking.service.interfaces.IShowtimeSeatMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/showtimes/public")
public class PublicSeatMapController {

    private final IShowtimeSeatMapService showtimeSeatMapService;

    public PublicSeatMapController(IShowtimeSeatMapService showtimeSeatMapService) {
        this.showtimeSeatMapService = showtimeSeatMapService;
    }

    @GetMapping("/{showtimeId}/seat-map")
    public ResponseEntity<ApiResponse<ShowtimeSeatMapResponse>> getSeatMap(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(ApiResponse.success(showtimeSeatMapService.getSeatMap(showtimeId)));
    }
}
