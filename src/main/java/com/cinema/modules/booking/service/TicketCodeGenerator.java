package com.cinema.modules.booking.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TicketCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 14;
    private static final int QR_CODE_LENGTH = 24;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return randomCode("TK", CODE_LENGTH);
    }

    public String generateQrCode() {
        return randomCode("QR", QR_CODE_LENGTH);
    }

    private String randomCode(String prefix, int length) {
        StringBuilder code = new StringBuilder(prefix);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
