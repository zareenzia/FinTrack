package org.example.finzin.service;

import org.example.finzin.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test for JwtTokenProvider. No mocking is needed here — the class has a single,
 * side-effect-free collaborator (the secret key it's constructed with), so the most meaningful
 * tests exercise real JJWT sign/verify round trips, matching the real HS512 signing algorithm
 * used in production (see app.jwt.secret in application.properties, which is 88 characters).
 */
class JwtTokenProviderTest {

    // 88 chars — same length class as the real app.jwt.secret, comfortably over the 64-byte
    // minimum HS512 requires.
    private static final String SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-64-characters-long-1234567890AB==";
    private static final String OTHER_SECRET = "a-completely-different-test-secret-key-1234567890-abcdefghijklmnopqrstuvwxyzABCDEF";

    private JwtTokenProvider provider;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET);
        user = new UserEntity();
        user.setId(42L);
        user.setUsername("nabil");
        user.setEmail("nabil@example.com");
    }

    @Test
    void generateTokenProducesATokenThatValidatesAsTrue() {
        String token = provider.generateToken(user);

        assertNotNull(token);
        assertTrue(provider.validateToken(token));
    }

    @Test
    void extractUserIdRoundTripsTheUserId() {
        String token = provider.generateToken(user);

        assertEquals(42L, provider.extractUserId(token));
    }

    @Test
    void extractUsernameRoundTripsTheUsername() {
        String token = provider.generateToken(user);

        assertEquals("nabil", provider.extractUsername(token));
    }

    @Test
    void validateTokenReturnsFalseForAGarbageString() {
        assertFalse(provider.validateToken("this-is-not-a-jwt-at-all"));
    }

    @Test
    void validateTokenReturnsFalseForAnEmptyString() {
        assertFalse(provider.validateToken(""));
    }

    @Test
    void validateTokenReturnsFalseForATokenSignedWithADifferentKey() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(OTHER_SECRET);
        String tokenSignedByOther = otherProvider.generateToken(user);

        assertFalse(provider.validateToken(tokenSignedByOther),
                "a token signed with a different key must not validate against this provider's key");
    }

    @Test
    void extractUserIdReturnsNullForAnInvalidToken() {
        assertNull(provider.extractUserId("garbage"));
    }

    @Test
    void extractUsernameReturnsNullForAnInvalidToken() {
        assertNull(provider.extractUsername("garbage"));
    }
}
