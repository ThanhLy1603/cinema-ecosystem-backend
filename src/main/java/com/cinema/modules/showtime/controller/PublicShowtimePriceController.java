package com.cinema.modules.showtime.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.showtime.dto.response.ShowtimePriceResponse;
import com.cinema.modules.showtime.service.interfaces.IDynamicPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/showtimes/public")
public class PublicShowtimePriceController {

    private final IDynamicPricingService dynamicPricingService;

    public PublicShowtimePriceController(IDynamicPricingService dynamicPricingService) {
        this.dynamicPricingService = dynamicPricingService;
    }

    @GetMapping("/{showtimeId}/prices")
    public ResponseEntity<ApiResponse<ShowtimePriceResponse>> getShowtimePrices(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(ApiResponse.success(dynamicPricingService.getShowtimePrices(showtimeId)));
    }
}
