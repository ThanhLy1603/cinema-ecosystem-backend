package com.cinema.modules.staff.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.dto.response.CustomerLookupResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.auth.service.interfaces.IUserQueryService;
import com.cinema.modules.payment.dto.request.CounterCheckoutRequest;
import com.cinema.modules.payment.dto.response.CounterReceiptResponse;
import com.cinema.modules.payment.service.interfaces.ICounterPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/ticket-sales")
public class StaffTicketSalesController {

    private final ICounterPaymentService counterPaymentService;
    private final IUserQueryService userQueryService;

    public StaffTicketSalesController(
            ICounterPaymentService counterPaymentService, IUserQueryService userQueryService) {
        this.counterPaymentService = counterPaymentService;
        this.userQueryService = userQueryService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CounterReceiptResponse>> checkout(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CounterCheckoutRequest counterCheckoutRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                counterPaymentService.checkout(authenticatedUser.userId(), counterCheckoutRequest)));
    }

    @GetMapping("/customers/lookup")
    public ResponseEntity<ApiResponse<CustomerLookupResponse>> lookupCustomer(@RequestParam String keyword) {
        return ResponseEntity.ok(ApiResponse.success(
                userQueryService.findActiveCustomerByKeyword(keyword).orElse(null)));
    }
}
