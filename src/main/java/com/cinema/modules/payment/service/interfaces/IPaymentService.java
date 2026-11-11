package com.cinema.modules.payment.service.interfaces;

import com.cinema.modules.payment.dto.request.SimulatePaymentRequest;
import com.cinema.modules.payment.dto.response.PaymentResultResponse;
import com.cinema.modules.payment.dto.response.PaymentUrlResponse;
import com.cinema.modules.payment.dto.response.VnpayIpnResponse;

import java.util.Map;

public interface IPaymentService {

    PaymentUrlResponse createVnpayPayment(Long userId, String clientIp);

    VnpayIpnResponse processVnpayIpn(Map<String, String> params);

    PaymentResultResponse getPaymentResult(Long userId, String transactionRef);

    VnpayIpnResponse simulateGatewayCallback(Long userId, SimulatePaymentRequest simulatePaymentRequest);
}
