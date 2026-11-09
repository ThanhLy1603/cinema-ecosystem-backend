package com.cinema.modules.auth.security;

import com.cinema.common.exception.AppException;
import com.cinema.modules.auth.model.GoogleProfile;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "cinema-client-id.apps.googleusercontent.com";
    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    private KeyPair keyPair;
    private KeyPair otherKeyPair;
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        keyPair = generateKeyPair();
        otherKeyPair = generateKeyPair();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        NimbusJwtDecoder nimbusJwtDecoder =
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        JwtDecoder jwtDecoder = GoogleIdTokenVerifier.withValidators(nimbusJwtDecoder, CLIENT_ID, clock);
        googleIdTokenVerifier = new GoogleIdTokenVerifier(jwtDecoder, true);
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-sub-1")
                .claim("email", "an@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Nguyễn An")
                .claim("picture", "https://lh3.googleusercontent.com/a/avatar")
                .issueTime(Date.from(NOW.minusSeconds(60)))
                .expirationTime(Date.from(NOW.plusSeconds(3600)));
    }

    private String sign(JWTClaimsSet.Builder claimsBuilder, KeyPair signingKeyPair) throws JOSEException {
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsBuilder.build());
        signedJwt.sign(new RSASSASigner(signingKeyPair.getPrivate()));
        return signedJwt.serialize();
    }

    private void assertRejected(String idToken, HttpStatus expectedStatus, String expectedErrorCode) {
        AppException appException = assertThrows(AppException.class, () -> googleIdTokenVerifier.verify(idToken));
        assertEquals(expectedStatus, appException.getStatus());
        assertEquals(expectedErrorCode, appException.getErrorCode());
    }

    @Test
    void validTokenReturnsTheGoogleProfile() throws Exception {
        GoogleProfile googleProfile = googleIdTokenVerifier.verify(sign(validClaims(), keyPair));

        assertEquals("google-sub-1", googleProfile.subject());
        assertEquals("an@gmail.com", googleProfile.email());
        assertEquals("Nguyễn An", googleProfile.fullName());
        assertEquals("https://lh3.googleusercontent.com/a/avatar", googleProfile.avatarUrl());
    }

    @Test
    void issuerWithoutSchemeIsAlsoAccepted() throws Exception {
        String idToken = sign(validClaims().issuer("accounts.google.com"), keyPair);

        assertEquals("google-sub-1", googleIdTokenVerifier.verify(idToken).subject());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        assertRejected(sign(validClaims(), otherKeyPair), HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN");
    }

    @Test
    void tokenForAnotherClientIdIsRejected() throws Exception {
        assertRejected(sign(validClaims().audience("someone-else"), keyPair), HttpStatus.UNAUTHORIZED,
                "INVALID_GOOGLE_TOKEN");
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        assertRejected(sign(validClaims().issuer("https://evil.example.com"), keyPair), HttpStatus.UNAUTHORIZED,
                "INVALID_GOOGLE_TOKEN");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String idToken = sign(validClaims().expirationTime(Date.from(NOW.minusSeconds(600))), keyPair);

        assertRejected(idToken, HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN");
    }

    @Test
    void garbageTokenIsRejected() {
        assertRejected("not-a-jwt", HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN");
    }

    @Test
    void unverifiedGoogleEmailIsRejected() throws Exception {
        assertRejected(sign(validClaims().claim("email_verified", false), keyPair), HttpStatus.FORBIDDEN,
                "GOOGLE_EMAIL_NOT_VERIFIED");
    }

    @Test
    void missingEmailClaimIsRejected() throws Exception {
        assertRejected(sign(validClaims().claim("email", null), keyPair), HttpStatus.UNAUTHORIZED,
                "INVALID_GOOGLE_TOKEN");
    }

    @Test
    void unconfiguredClientIdDisablesGoogleLogin() {
        GoogleIdTokenVerifier disabledVerifier = new GoogleIdTokenVerifier(idToken -> {
            throw new JwtException("must not be called");
        }, false);

        AppException appException = assertThrows(AppException.class, () -> disabledVerifier.verify("any"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, appException.getStatus());
        assertEquals("GOOGLE_LOGIN_DISABLED", appException.getErrorCode());
    }

    @Test
    void remoteKeyFailureIsReportedAsUnavailableNotAsInvalidToken() {
        GoogleIdTokenVerifier brokenVerifier = new GoogleIdTokenVerifier(idToken -> {
            throw new JwtException("Couldn't retrieve remote JWK set");
        }, true);

        AppException appException = assertThrows(AppException.class, () -> brokenVerifier.verify("any"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, appException.getStatus());
        assertEquals("GOOGLE_UNAVAILABLE", appException.getErrorCode());
    }
}
