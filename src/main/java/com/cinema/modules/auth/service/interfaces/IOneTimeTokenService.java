package com.cinema.modules.auth.service.interfaces;

import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserToken;
import com.cinema.modules.auth.enums.UserTokenType;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface IOneTimeTokenService {

    String issue(User user, UserTokenType userTokenType, Duration ttl, String ipAddress, Instant now);

    Optional<UserToken> findUsableForUpdate(String rawToken, UserTokenType userTokenType, Instant now);
}
