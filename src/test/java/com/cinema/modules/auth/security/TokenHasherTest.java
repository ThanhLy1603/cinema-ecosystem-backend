package com.cinema.modules.auth.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenHasherTest {

    @Test
    void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TokenHasher.sha256Hex("abc"));
    }

    @Test
    void sha256IsDeterministicAnd64HexCharacters() {
        String tokenHash = TokenHasher.sha256Hex("some-token");

        assertEquals(tokenHash, TokenHasher.sha256Hex("some-token"));
        assertNotEquals(tokenHash, TokenHasher.sha256Hex("some-token2"));
        assertTrue(tokenHash.matches("^[0-9a-f]{64}$"));
    }

    @Test
    void randomTokensAreUniqueAndUrlSafe() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = TokenHasher.generateRandomToken();
            assertEquals(43, token.length());
            assertTrue(token.matches("^[A-Za-z0-9_-]+$"));
            tokens.add(token);
        }
        assertEquals(1000, tokens.size());
    }
}
