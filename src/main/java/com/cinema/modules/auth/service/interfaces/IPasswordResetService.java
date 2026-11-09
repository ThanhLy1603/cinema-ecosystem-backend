package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.request.ForgotPasswordRequest;
import com.cinema.modules.auth.dto.request.ResetPasswordRequest;

public interface IPasswordResetService {

    void requestReset(ForgotPasswordRequest forgotPasswordRequest, String ipAddress);

    void resetPassword(ResetPasswordRequest resetPasswordRequest);
}
