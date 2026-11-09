package com.cinema.modules.auth.service;

import com.cinema.modules.auth.entity.User;
import com.cinema.modules.auth.entity.UserToken;
import com.cinema.modules.auth.enums.UserTokenType;
import com.cinema.modules.auth.repository.UserTokenRepository;
import com.cinema.modules.auth.security.TokenHasher;
import com.cinema.modules.auth.service.interfaces.IOneTimeTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class OneTimeTokenService implements IOneTimeTokenService {

    private static final int IP_ADDRESS_MAX_LENGTH = 45;

    private final UserTokenRepository userTokenRepository;

    public OneTimeTokenService(UserTokenRepository userTokenRepository) {
        this.userTokenRepository = userTokenRepository;
    }

    @Transactional
    @Override
    public String issue(User user, UserTokenType userTokenType, Duration ttl, String ipAddress, Instant now) {
        userTokenRepository.invalidateUnused(user.getId(), userTokenType, now);

        String rawToken = TokenHasher.generateRandomToken();
        UserToken userToken = new UserToken();
        userToken.setUser(user);
        userToken.setTokenType(userTokenType);
        userToken.setTokenHash(TokenHasher.sha256Hex(rawToken));
        userToken.setExpiresAt(now.plus(ttl));
        userToken.setIpAddress(truncate(ipAddress));
        userTokenRepository.save(userToken);
        return rawToken;
    }

    @Transactional
    @Override
    public Optional<UserToken> findUsableForUpdate(String rawToken, UserTokenType userTokenType, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return userTokenRepository
                .findByTokenHashAndTypeForUpdate(TokenHasher.sha256Hex(rawToken.trim()), userTokenType)
                .filter(userToken -> userToken.isUsable(now));
    }

    private String truncate(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        return ipAddress.length() <= IP_ADDRESS_MAX_LENGTH ? ipAddress : ipAddress.substring(0, IP_ADDRESS_MAX_LENGTH);
    }
}
