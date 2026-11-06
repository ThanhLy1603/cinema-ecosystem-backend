package com.cinema.modules.auth.entity;

import com.cinema.modules.auth.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Test
    void newUserHasSafeDefaults() {
        User user = new User();

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals("STAR", user.getTier().name());
        assertEquals(0, user.getLoyaltyPoints());
        assertFalse(user.isEmailVerified());
        assertFalse(user.hasPassword());
    }

    @Test
    void lockedAfterMaxFailedAttempts() {
        User user = new User();

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            user.registerFailedLogin(MAX_ATTEMPTS, LOCK, NOW);
        }
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.isLoginAllowed(NOW));

        user.registerFailedLogin(MAX_ATTEMPTS, LOCK, NOW);
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertEquals(NOW.plus(LOCK), user.getLockedUntil());
        assertFalse(user.isLoginAllowed(NOW.plusSeconds(60)));
    }

    @Test
    void lockExpiresAndCounterStartsOver() {
        User user = new User();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            user.registerFailedLogin(MAX_ATTEMPTS, LOCK, NOW);
        }
        Instant afterLock = NOW.plus(LOCK).plusSeconds(1);
        assertTrue(user.isLoginAllowed(afterLock));

        user.registerFailedLogin(MAX_ATTEMPTS, LOCK, afterLock);
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(1, user.getFailedLoginAttempts());
    }

    @Test
    void successfulLoginClearsFailuresAndTemporaryLock() {
        User user = new User();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            user.registerFailedLogin(MAX_ATTEMPTS, LOCK, NOW);
        }
        Instant later = NOW.plus(LOCK).plusSeconds(1);

        user.recordSuccessfulLogin(later);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertEquals(later, user.getLastLoginAt());
    }

    @Test
    void suspendedAndDeletedAreNeverAllowedAndNeverAutoUnlocked() {
        User suspendedUser = new User();
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        assertFalse(suspendedUser.isLoginAllowed(NOW));

        suspendedUser.recordSuccessfulLogin(NOW);
        assertEquals(UserStatus.SUSPENDED, suspendedUser.getStatus());

        User deletedUser = new User();
        deletedUser.setStatus(UserStatus.DELETED);
        assertFalse(deletedUser.isLoginAllowed(NOW));
    }

    @Test
    void rolesAreComparedByCode() {
        Role firstRole = new Role();
        firstRole.setCode(Role.STAFF);
        Role secondRole = new Role();
        secondRole.setCode(Role.STAFF);

        User user = new User();
        user.addRole(firstRole);
        user.addRole(secondRole);

        assertEquals(1, user.getRoles().size());
        assertTrue(user.hasRole(Role.STAFF));
        assertFalse(user.hasRole(Role.ADMIN));
    }

    @Test
    void passwordlessGoogleAccountHasNoPassword() {
        User user = new User();
        user.setPasswordHash(null);
        assertFalse(user.hasPassword());

        user.setPasswordHash("$2a$12$hash");
        assertTrue(user.hasPassword());
    }

    @Test
    void tokensExpireAndCanOnlyBeUsedOnce() {
        UserToken userToken = new UserToken();
        userToken.setExpiresAt(NOW.plusSeconds(1800));
        assertTrue(userToken.isUsable(NOW));

        userToken.markUsed(NOW);
        assertNotNull(userToken.getUsedAt());
        assertFalse(userToken.isUsable(NOW));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setExpiresAt(NOW.plusSeconds(60));
        assertTrue(refreshToken.isActive(NOW));
        assertFalse(refreshToken.isActive(NOW.plusSeconds(61)));

        refreshToken.revoke(NOW);
        assertTrue(refreshToken.isRevoked());
        assertFalse(refreshToken.isActive(NOW));
    }
}
