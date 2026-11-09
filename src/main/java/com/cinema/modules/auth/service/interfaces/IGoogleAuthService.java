package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.request.GoogleLoginRequest;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;

public interface IGoogleAuthService {

    AuthSession login(GoogleLoginRequest googleLoginRequest, ClientInfo clientInfo);
}
