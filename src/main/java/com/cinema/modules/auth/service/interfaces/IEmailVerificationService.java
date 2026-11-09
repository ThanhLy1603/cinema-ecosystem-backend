package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.request.VerifyEmailRequest;

public interface IEmailVerificationService {

    void sendVerification(Long userId, String ipAddress);

    void resendVerification(Long userId, String ipAddress);

    void verifyEmail(VerifyEmailRequest verifyEmailRequest);
}
