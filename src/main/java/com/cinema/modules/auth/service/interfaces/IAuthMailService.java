package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.event.EmailVerificationRequestedEvent;
import com.cinema.modules.auth.event.PasswordResetRequestedEvent;

public interface IAuthMailService {

    void onPasswordResetRequested(PasswordResetRequestedEvent passwordResetRequestedEvent);

    void onEmailVerificationRequested(EmailVerificationRequestedEvent emailVerificationRequestedEvent);
}
