package com.cinema.modules.auth.security;

import com.cinema.config.JwtProperties;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String ISSUER = "cinema-ecosystem";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        byte[] secretBytes = jwtProperties.secret() == null
                ? new byte[0]
                : jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret phải dài tối thiểu " + MIN_SECRET_BYTES + " byte (256 bit) cho HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenTtl = Duration.ofSeconds(jwtProperties.accessTokenExpirationSeconds());
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        Instant issuedAt = clock.instant();
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roleCodes)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<AuthenticatedUser> parseAccessToken(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String email = claims.get(CLAIM_EMAIL, String.class);
            List<?> rawRoleCodes = claims.get(CLAIM_ROLES, List.class);
            if (rawRoleCodes == null) {
                return Optional.empty();
            }
            List<String> roleCodes = rawRoleCodes.stream().map(String::valueOf).toList();
            return Optional.of(new AuthenticatedUser(userId, email, roleCodes));
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Access Token không hợp lệ: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }
}
