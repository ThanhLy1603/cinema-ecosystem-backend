package com.cinema.modules.payment.service;

import com.cinema.config.properties.VnpayProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class VnpaySigner {

    static final String SECURE_HASH_FIELD = "vnp_SecureHash";
    static final String SECURE_HASH_TYPE_FIELD = "vnp_SecureHashType";
    private static final String HMAC_SHA512 = "HmacSHA512";

    private final VnpayProperties vnpayProperties;

    public VnpaySigner(VnpayProperties vnpayProperties) {
        this.vnpayProperties = vnpayProperties;
    }

    public String sign(Map<String, String> params) {
        return hmacSha512(buildQuery(params));
    }

    public boolean verify(Map<String, String> params) {
        String receivedHash = params.get(SECURE_HASH_FIELD);
        if (receivedHash == null || receivedHash.isBlank() || vnpayProperties.hashSecret().isBlank()) {
            return false;
        }
        byte[] expectedBytes = sign(params).getBytes(StandardCharsets.US_ASCII);
        byte[] receivedBytes = receivedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    public String buildPayUrl(Map<String, String> params) {
        return vnpayProperties.payUrl() + "?" + buildQuery(params) + "&" + SECURE_HASH_FIELD + "=" + sign(params);
    }

    private String buildQuery(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vnp_"))
                .filter(entry -> !SECURE_HASH_FIELD.equals(entry.getKey()))
                .filter(entry -> !SECURE_HASH_TYPE_FIELD.equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(new SecretKeySpec(vnpayProperties.hashSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Không thể ký dữ liệu thanh toán", exception);
        }
    }
}
