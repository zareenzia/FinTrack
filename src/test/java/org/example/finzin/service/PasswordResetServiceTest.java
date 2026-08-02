package org.example.finzin.service;

import org.example.finzin.entity.PasswordResetTokenEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.PasswordResetTokenRepository;
import org.example.finzin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for PasswordResetService's forgot-password token flow: generation on
 * requestReset, and validation (valid/expired/unknown/consumed) on resetPassword. The service
 * hashes raw tokens with SHA-256 before ever touching the repository/DB, so tests capture the
 * generated token via ArgumentCaptor and independently recompute its SHA-256 hex to know what
 * "the same token" looks like from the caller's side.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private AuthService authService;
    @Mock private EmailService emailService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, authService, emailService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://app.example.com");
    }

    private UserEntity user(Long id, String email, String fullName) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setEmail(email);
        u.setFullName(fullName);
        return u;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ================================================================================
    // requestReset
    // ================================================================================

    @Test
    void requestResetNoOpsSilentlyWhenEmailDoesNotMatchAnAccount() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        service.requestReset("unknown@example.com");

        verifyNoInteractions(tokenRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void requestResetDeletesAnyExistingTokenBeforeIssuingANewOne() {
        UserEntity user = user(USER_ID, "user@example.com", "Jane Doe");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        service.requestReset("user@example.com");

        verify(tokenRepository).deleteByUserId(USER_ID);
    }

    @Test
    void requestResetSavesHashedTokenWithExpiryAndSendsResetEmailWithRawTokenLink() {
        UserEntity user = user(USER_ID, "user@example.com", "Jane Doe");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        service.requestReset("user@example.com");

        ArgumentCaptor<PasswordResetTokenEntity> captor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetTokenEntity saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(64, saved.getTokenHash().length(), "SHA-256 hex digest must be 64 characters");

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("user@example.com"), eq("Jane Doe"), linkCaptor.capture());
        String link = linkCaptor.getValue();
        assertEquals(true, link.startsWith("https://app.example.com/reset-password?token="));

        String rawToken = link.substring(link.indexOf("token=") + "token=".length());
        assertEquals(saved.getTokenHash(), sha256Hex(rawToken), "stored hash must match the SHA-256 of the raw token embedded in the email link");
    }

    // ================================================================================
    // resetPassword
    // ================================================================================

    @Test
    void resetPasswordThrowsWhenTokenDoesNotExist() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword("some-raw-token", "NewPassw0rd!"));

        assertEquals("This reset link is invalid or has already been used.", ex.getMessage());
        verifyNoInteractions(authService);
    }

    @Test
    void resetPasswordThrowsAndDeletesTokenWhenExpired() {
        String rawToken = "raw-token-value";
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(1L);
        entity.setUserId(USER_ID);
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(rawToken, "NewPassw0rd!"));

        assertEquals("This reset link has expired. Please request a new one.", ex.getMessage());
        verify(tokenRepository).delete(entity);
        verifyNoInteractions(authService);
    }

    @Test
    void resetPasswordAppliesNewPasswordAndConsumesTokenWhenValid() {
        String rawToken = "raw-token-value";
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(1L);
        entity.setUserId(USER_ID);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(entity));

        service.resetPassword(rawToken, "NewPassw0rd!");

        verify(authService).resetPassword(USER_ID, "NewPassw0rd!");
        verify(tokenRepository).delete(entity);
        verify(tokenRepository, never()).findByTokenHash(sha256Hex(rawToken) + "x");
    }

    @Test
    void resetPasswordPropagatesValidationFailureFromAuthServiceAndDoesNotDeleteTokenOnFailure() {
        String rawToken = "raw-token-value";
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(1L);
        entity.setUserId(USER_ID);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(entity));
        when(authService.resetPassword(eq(USER_ID), anyString()))
                .thenThrow(new IllegalArgumentException("Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character"));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(rawToken, "weak"));

        verify(tokenRepository, never()).delete(any());
    }
}
