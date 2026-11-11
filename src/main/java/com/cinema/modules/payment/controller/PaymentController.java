package com.cinema.modules.payment.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.auth.security.AuthenticatedUser;
import com.cinema.modules.payment.dto.request.SimulatePaymentRequest;
import com.cinema.modules.payment.dto.response.PaymentResultResponse;
import com.cinema.modules.payment.dto.response.PaymentUrlResponse;
import com.cinema.modules.payment.dto.response.VnpayIpnResponse;
import com.cinema.modules.payment.service.interfaces.IPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final String RSP_UNKNOWN_ERROR = "99";

    private final IPaymentService paymentService;

    public PaymentController(IPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/vnpay/create")
    public ResponseEntity<ApiResponse<PaymentUrlResponse>> createVnpayPayment(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.createVnpayPayment(authenticatedUser.userId(), httpServletRequest.getRemoteAddr())));
    }

    @GetMapping("/vnpay-ipn")
    public ResponseEntity<VnpayIpnResponse> handleVnpayIpn(HttpServletRequest httpServletRequest) {
        Map<String, String> params = new HashMap<>();
        httpServletRequest.getParameterMap().forEach((name, values) -> {
            if (values.length > 0) {
                params.put(name, values[0]);
            }
        });
        try {
            return ResponseEntity.ok(paymentService.processVnpayIpn(params));
        } catch (RuntimeException runtimeException) {
            log.error("Không xử lý được IPN VNPay", runtimeException);
            return ResponseEntity.ok(new VnpayIpnResponse(RSP_UNKNOWN_ERROR, "Unknown error"));
        }
    }

    @GetMapping("/result/{transactionRef}")
    public ResponseEntity<ApiResponse<PaymentResultResponse>> getPaymentResult(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable String transactionRef) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPaymentResult(authenticatedUser.userId(), transactionRef)));
    }

    @PostMapping("/simulator/complete")
    public ResponseEntity<ApiResponse<VnpayIpnResponse>> simulateGatewayCallback(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody SimulatePaymentRequest simulatePaymentRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.simulateGatewayCallback(authenticatedUser.userId(), simulatePaymentRequest)));
    }
}
