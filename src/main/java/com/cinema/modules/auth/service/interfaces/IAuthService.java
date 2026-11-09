package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.dto.request.LoginRequest;
import com.cinema.modules.auth.dto.request.RegisterRequest;
import com.cinema.modules.auth.dto.response.UserResponse;
import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.model.AuthSession;
import com.cinema.modules.auth.model.ClientInfo;

public interface IAuthService {

    UserResponse register(RegisterRequest registerRequest);

    AuthSession login(LoginRequest loginRequest, ClientInfo clientInfo);

    AuthSession openSession(User user, ClientInfo clientInfo);

    AuthSession refresh(String rawRefreshToken, ClientInfo clientInfo);

    void logout(String rawRefreshToken);

    void logoutAll(Long userId);
}
