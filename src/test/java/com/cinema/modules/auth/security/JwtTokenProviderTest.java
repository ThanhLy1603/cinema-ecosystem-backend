package com.cinema.modules.auth.security;

import com.cinema.config.JwtProperties;
import com.cinema.modules.auth.entity.Role;
import com.cinema.modules.auth.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant ISSUED_AT = Instant.parse("2026-11-06T10:00:00Z");
    private static final long ACCESS_TTL_SECONDS = 900;

    private static JwtTokenProvider providerAt(Instant now, String secret) {
        return new JwtTokenProvider(
                new JwtProperties(secret, ACCESS_TTL_SECONDS, 604800),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User userWithRoles(String... roleCodes) {
        User user = new User();
        user.setId(42L);
        user.setEmail("an@cinema.vn");
        for (String roleCode : roleCodes) {
            Role role = new Role();
            role.setCode(roleCode);
            user.addRole(role);
        }
        return user;
    }

    @Test
    void roundTripKeepsUserIdEmailAndRoles() {
        JwtTokenProvider jwtTokenProvider = providerAt(ISSUED_AT, SECRET);

        String accessToken = jwtTokenProvider.generateAccessToken(userWithRoles(Role.STAFF, Role.MANAGER));
        Optional<AuthenticatedUser> optionalAuthenticatedUser = jwtTokenProvider.parseAccessToken(accessToken);

        assertTrue(optionalAuthenticatedUser.isPresent());
        AuthenticatedUser authenticatedUser = optionalAuthenticatedUser.get();
        assertEquals(42L, authenticatedUser.userId());
        assertEquals("an@cinema.vn", authenticatedUser.email());
        assertEquals(List.of(Role.MANAGER, Role.STAFF), authenticatedUser.roles());
    }

    @Test
    void tokenIsValidUntilTtlThenExpires() {
        String accessToken = providerAt(ISSUED_AT, SECRET).generateAccessToken(userWithRoles(Role.CUSTOMER));

        assertTrue(providerAt(ISSUED_AT.plusSeconds(ACCESS_TTL_SECONDS - 1), SECRET)
                .parseAccessToken(accessToken).isPresent());
        assertFalse(providerAt(ISSUED_AT.plusSeconds(ACCESS_TTL_SECONDS + 1), SECRET)
                .parseAccessToken(accessToken).isPresent());
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String forgedToken = providerAt(ISSUED_AT, "ffffffffffffffffffffffffffffffffffffffffffffffff")
                .generateAccessToken(userWithRoles(Role.ADMIN));

        assertFalse(providerAt(ISSUED_AT, SECRET).parseAccessToken(forgedToken).isPresent());
    }

    @Test
    void rejectsTamperedPayload() {
        JwtTokenProvider jwtTokenProvider = providerAt(ISSUED_AT, SECRET);
        String[] parts = jwtTokenProvider.generateAccessToken(userWithRoles(Role.CUSTOMER)).split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"cinema-ecosystem\",\"sub\":\"1\",\"roles\":[\"ROLE_ADMIN\"]}"
                        .getBytes(StandardCharsets.UTF_8));

        assertFalse(jwtTokenProvider.parseAccessToken(parts[0] + "." + forgedPayload + "." + parts[2]).isPresent());
    }

    @Test
    void rejectsUnsignedAndMalformedTokens() {
        JwtTokenProvider jwtTokenProvider = providerAt(ISSUED_AT, SECRET);

        assertFalse(jwtTokenProvider.parseAccessToken("eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.").isPresent());
        assertFalse(jwtTokenProvider.parseAccessToken("not-a-jwt").isPresent());
        assertFalse(jwtTokenProvider.parseAccessToken("").isPresent());
    }

    @Test
    void rejectsTokenFromAnotherIssuerOrWithoutRoles() {
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date expiration = Date.from(ISSUED_AT.plusSeconds(60));
        JwtTokenProvider jwtTokenProvider = providerAt(ISSUED_AT, SECRET);

        String otherIssuerToken = Jwts.builder().issuer("someone-else").subject("1")
                .claim("roles", List.of(Role.ADMIN)).expiration(expiration).signWith(secretKey).compact();
        String noRolesToken = Jwts.builder().issuer("cinema-ecosystem").subject("1")
                .expiration(expiration).signWith(secretKey).compact();

        assertFalse(jwtTokenProvider.parseAccessToken(otherIssuerToken).isPresent());
        assertFalse(jwtTokenProvider.parseAccessToken(noRolesToken).isPresent());
    }

    @Test
    void refusesToStartWithWeakOrMissingSecret() {
        assertThrows(IllegalStateException.class, () -> providerAt(ISSUED_AT, "too-short"));
        assertThrows(IllegalStateException.class, () -> providerAt(ISSUED_AT, null));
    }
}
