package com.cinema.modules.staff.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.payment.dto.request.CounterFnbCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterFnbReceiptResponse;
import com.cinema.modules.payment.dto.response.CounterReceiptFileResponse;
import com.cinema.modules.payment.service.interfaces.ICounterFnbSaleService;
import com.cinema.modules.payment.service.interfaces.ICounterReceiptFileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/fnb-sales")
public class StaffFnbSalesController {

    private final ICounterFnbSaleService counterFnbSaleService;
    private final ICounterReceiptFileService counterReceiptFileService;

    public StaffFnbSalesController(
            ICounterFnbSaleService counterFnbSaleService, ICounterReceiptFileService counterReceiptFileService) {
        this.counterFnbSaleService = counterFnbSaleService;
        this.counterReceiptFileService = counterReceiptFileService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CounterFnbReceiptResponse>> checkout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CounterFnbCheckoutRequest counterFnbCheckoutRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                counterFnbSaleService.checkout(authenticatedUser.userId(), counterFnbCheckoutRequest)));
    }

    @PostMapping("/receipts/{orderCode}/save")
    public ResponseEntity<ApiResponse<CounterReceiptFileResponse>> saveReceiptFile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.success(
                counterReceiptFileService.saveFnbReceiptFile(authenticatedUser.userId(), orderCode)));
    }
}
