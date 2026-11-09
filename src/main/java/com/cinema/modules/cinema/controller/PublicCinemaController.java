package com.cinema.modules.cinema.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.cinema.dto.response.CinemaDetailResponse;
import com.cinema.modules.cinema.dto.response.CinemaResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaPublicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cinemas/public")
public class PublicCinemaController {

    private final ICinemaPublicService cinemaPublicService;

    public PublicCinemaController(ICinemaPublicService cinemaPublicService) {
        this.cinemaPublicService = cinemaPublicService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaResponse>>> getCinemas(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(ApiResponse.success(cinemaPublicService.findPublicCinemas(city)));
    }

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<String>>> getCities() {
        return ResponseEntity.ok(ApiResponse.success(cinemaPublicService.findPublicCities()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CinemaDetailResponse>> getCinemaBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(cinemaPublicService.findPublicCinemaBySlug(slug)));
    }
}
