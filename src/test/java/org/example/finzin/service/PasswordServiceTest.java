package org.example.finzin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for PasswordService, a thin wrapper around Spring Security's
 * PasswordEncoder. Delegation is verified with a mocked PasswordEncoder, and a second set of
 * tests wires up a real BCryptPasswordEncoder to prove the round trip and salting behavior
 * actually work end to end.
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock private PasswordEncoder passwordEncoder;

    // ================================================================================
    // Delegation (mocked encoder)
    // ================================================================================

    @Test
    void hashPasswordDelegatesToEncoderEncode() {
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-hash");
        PasswordService service = new PasswordService(passwordEncoder);

        String result = service.hashPassword("raw-password");

        assertEquals("encoded-hash", result);
        verify(passwordEncoder).encode("raw-password");
    }

    @Test
    void verifyPasswordDelegatesToEncoderMatches() {
        when(passwordEncoder.matches("raw-password", "stored-hash")).thenReturn(true);
        PasswordService service = new PasswordService(passwordEncoder);

        boolean result = service.verifyPassword("raw-password", "stored-hash");

        assertTrue(result);
        verify(passwordEncoder).matches("raw-password", "stored-hash");
    }

    @Test
    void verifyPasswordReturnsFalseWhenEncoderReportsNoMatch() {
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);
        PasswordService service = new PasswordService(passwordEncoder);

        assertFalse(service.verifyPassword("wrong-password", "stored-hash"));
    }

    // ================================================================================
    // Real BCryptPasswordEncoder round trip
    // ================================================================================

    @Test
    void hashPasswordProducesAHashDifferentFromTheRawPassword() {
        PasswordService service = new PasswordService(new BCryptPasswordEncoder());

        String hash = service.hashPassword("Sup3rSecret!");

        assertNotEquals("Sup3rSecret!", hash);
        assertTrue(hash.startsWith("$2"), "BCrypt hashes start with the $2 prefix");
    }

    @Test
    void hashingTheSamePasswordTwiceProducesDifferentHashesDueToSalting() {
        PasswordService service = new PasswordService(new BCryptPasswordEncoder());

        String hash1 = service.hashPassword("Sup3rSecret!");
        String hash2 = service.hashPassword("Sup3rSecret!");

        assertNotEquals(hash1, hash2, "BCrypt must salt each hash independently");
        assertTrue(service.verifyPassword("Sup3rSecret!", hash1));
        assertTrue(service.verifyPassword("Sup3rSecret!", hash2));
    }

    @Test
    void verifyPasswordRoundTripsWithARealEncoder() {
        PasswordService service = new PasswordService(new BCryptPasswordEncoder());
        String hash = service.hashPassword("correct-horse-battery-staple");

        assertTrue(service.verifyPassword("correct-horse-battery-staple", hash));
        assertFalse(service.verifyPassword("wrong-password", hash));
    }
}
