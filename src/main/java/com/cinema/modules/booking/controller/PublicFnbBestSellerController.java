package com.cinema.modules.booking.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.booking.dto.response.FnbBestSellerResponse;
import com.cinema.modules.booking.service.interfaces.IBookingFnbSalesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fnb/public/best-sellers")
public class PublicFnbBestSellerController {

    private final IBookingFnbSalesService bookingFnbSalesService;

    public PublicFnbBestSellerController(IBookingFnbSalesService bookingFnbSalesService) {
        this.bookingFnbSalesService = bookingFnbSalesService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FnbBestSellerResponse>> findBestSellers() {
        return ResponseEntity.ok(ApiResponse.success(bookingFnbSalesService.findBestSellers()));
    }
}
