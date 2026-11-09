package com.cinema.modules.auth.security;

import com.cinema.common.exception.AppException;
import com.cinema.config.GoogleProperties;
import com.cinema.modules.auth.model.GoogleProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class GoogleIdTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> TRUSTED_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final String INVALID_TOKEN_ERROR_CODE = "invalid_token";

    private final JwtDecoder jwtDecoder;
    private final boolean configured;

    @Autowired
    public GoogleIdTokenVerifier(GoogleProperties googleProperties, Clock clock) {
        this(
                withValidators(
                        NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build(), googleProperties.clientId(), clock),
                googleProperties.isConfigured());
    }

    GoogleIdTokenVerifier(JwtDecoder jwtDecoder, boolean configured) {
        this.jwtDecoder = jwtDecoder;
        this.configured = configured;
    }

    public GoogleProfile verify(String idToken) {
        if (!configured) {
            throw new AppException("Đăng nhập bằng Google chưa được cấu hình", HttpStatus.SERVICE_UNAVAILABLE,
                    "GOOGLE_LOGIN_DISABLED");
        }

        Jwt jwt = decode(idToken);

        if (!isEmailVerified(jwt)) {
            throw new AppException("Email Google chưa được xác thực", HttpStatus.FORBIDDEN,
                    "GOOGLE_EMAIL_NOT_VERIFIED");
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw invalidGoogleToken();
        }
        return new GoogleProfile(subject, email, jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
    }

    private Jwt decode(String idToken) {
        try {
            return jwtDecoder.decode(idToken);
        } catch (BadJwtException badJwtException) {
            throw invalidGoogleToken();
        } catch (JwtException jwtException) {
            throw new AppException("Không thể xác thực với Google lúc này, vui lòng thử lại sau",
                    HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_UNAVAILABLE");
        }
    }

    private boolean isEmailVerified(Jwt jwt) {
        Object emailVerified = jwt.getClaims().get("email_verified");
        return Boolean.TRUE.equals(emailVerified) || "true".equalsIgnoreCase(String.valueOf(emailVerified));
    }

    private static AppException invalidGoogleToken() {
        return new AppException("Google ID Token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED,
                "INVALID_GOOGLE_TOKEN");
    }

    static JwtDecoder withValidators(NimbusJwtDecoder nimbusJwtDecoder, String clientId, Clock clock) {
        JwtTimestampValidator jwtTimestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        jwtTimestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> TRUSTED_ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(INVALID_TOKEN_ERROR_CODE));
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> audienceOf(jwt).contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(INVALID_TOKEN_ERROR_CODE));
        nimbusJwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(jwtTimestampValidator, issuerValidator, audienceValidator));
        return nimbusJwtDecoder;
    }

    private static List<String> audienceOf(Jwt jwt) {
        List<String> audiences = jwt.getAudience();
        return audiences == null ? List.of() : audiences;
    }
}
