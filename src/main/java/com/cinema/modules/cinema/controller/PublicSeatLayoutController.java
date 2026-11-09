package com.cinema.modules.cinema.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.cinema.dto.response.SeatLayoutResponse;
import com.cinema.modules.cinema.dto.response.SeatTypeResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaPublicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PublicSeatLayoutController {

    private final ICinemaPublicService cinemaPublicService;

    public PublicSeatLayoutController(ICinemaPublicService cinemaPublicService) {
        this.cinemaPublicService = cinemaPublicService;
    }

    @GetMapping("/halls/{hallId}/seat-layout")
    public ResponseEntity<ApiResponse<SeatLayoutResponse>> getSeatLayout(@PathVariable Long hallId) {
        return ResponseEntity.ok(ApiResponse.success(cinemaPublicService.findPublicSeatLayout(hallId)));
    }

    @GetMapping("/seat-types")
    public ResponseEntity<ApiResponse<List<SeatTypeResponse>>> getSeatTypes() {
        return ResponseEntity.ok(ApiResponse.success(cinemaPublicService.findSeatTypes()));
    }
}
