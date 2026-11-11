package com.cinema.modules.payment.service;

import com.cinema.config.properties.VnpayProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpaySignerTest {

    private final VnpaySigner vnpaySigner = new VnpaySigner(
            new VnpayProperties("TMN", "secret-key", "https://pay.example/vpcpay", "http://localhost/return", 15, false));

    private Map<String, String> signedParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "BK1-100");
        params.put("vnp_Amount", "9000000");
        params.put("vnp_OrderInfo", "Thanh toan ve BK1");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", vnpaySigner.sign(params));
        return params;
    }

    @Test
    void aSignedPayloadVerifiesAndIgnoresTheHashFieldsWhenSigning() {
        Map<String, String> params = signedParams();

        assertTrue(vnpaySigner.verify(params));
        params.put("vnp_SecureHashType", "HmacSHA512");
        assertTrue(vnpaySigner.verify(params));
    }

    @Test
    void anyTamperedFieldBreaksTheSignature() {
        Map<String, String> params = signedParams();
        params.put("vnp_Amount", "100");

        assertFalse(vnpaySigner.verify(params));
    }

    @Test
    void missingHashOrBlankSecretNeverVerifies() {
        Map<String, String> params = signedParams();
        params.remove("vnp_SecureHash");
        VnpaySigner unconfiguredSigner = new VnpaySigner(
                new VnpayProperties("", "", "https://pay.example", "http://localhost/return", 15, false));

        assertFalse(vnpaySigner.verify(params));
        assertFalse(unconfiguredSigner.verify(signedParams()));
    }

    @Test
    void theSignatureIsCaseInsensitiveHexAndStableForTheSameInput() {
        Map<String, String> params = signedParams();
        params.put("vnp_SecureHash", params.get("vnp_SecureHash").toUpperCase());

        assertTrue(vnpaySigner.verify(params));
        assertEquals(128, vnpaySigner.sign(params).length());
    }

    @Test
    void payUrlCarriesEncodedSortedFieldsAndTheHashLast() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_OrderInfo", "Thanh toan ve");
        params.put("vnp_Amount", "100");

        String payUrl = vnpaySigner.buildPayUrl(params);

        assertTrue(payUrl.startsWith("https://pay.example/vpcpay?vnp_Amount=100&vnp_OrderInfo=Thanh+toan+ve&vnp_SecureHash="));
    }
}
