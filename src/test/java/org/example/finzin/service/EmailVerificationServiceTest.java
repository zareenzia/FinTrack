package org.example.finzin.service;

import org.example.finzin.entity.EmailVerificationTokenEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.EmailVerificationTokenRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for EmailVerificationService, mirroring PasswordResetServiceTest's
 * approach: the service hashes raw tokens with SHA-256 before ever touching the repository/DB, so
 * tests capture the generated token via ArgumentCaptor and independently recompute its SHA-256 hex
 * to know what "the same token" looks like from the caller's side.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private EmailService emailService;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, tokenRepository, emailService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://app.example.com");
    }

    private UserEntity user(Long id, String email, String fullName, boolean verified) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setEmailVerified(verified);
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
    // sendVerificationEmail
    // ================================================================================

    @Test
    void sendVerificationEmailDeletesAnyExistingTokenBeforeIssuingANewOne() {
        UserEntity user = user(USER_ID, "user@example.com", "Jane Doe", false);

        service.sendVerificationEmail(user);

        verify(tokenRepository).deleteByUserId(USER_ID);
    }

    @Test
    void sendVerificationEmailSavesHashedTokenWithExpiryAndSendsEmailWithRawTokenLink() {
        UserEntity user = user(USER_ID, "user@example.com", "Jane Doe", false);

        service.sendVerificationEmail(user);

        ArgumentCaptor<EmailVerificationTokenEntity> captor = ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        EmailVerificationTokenEntity saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(64, saved.getTokenHash().length(), "SHA-256 hex digest must be 64 characters");

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq("user@example.com"), eq("Jane Doe"), linkCaptor.capture());
        String link = linkCaptor.getValue();
        assertTrue(link.startsWith("https://app.example.com/verify-email?token="));

        String rawToken = link.substring(link.indexOf("token=") + "token=".length());
        assertEquals(saved.getTokenHash(), sha256Hex(rawToken), "stored hash must match the SHA-256 of the raw token embedded in the email link");
    }

    // ================================================================================
    // verifyEmail
    // ================================================================================

    @Test
    void verifyEmailThrowsWhenTokenDoesNotExist() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.verifyEmail("some-raw-token"));

        assertEquals("This verification link is invalid or has already been used.", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyEmailThrowsAndDeletesTokenWhenExpired() {
        String rawToken = "raw-token-value";
        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
        entity.setId(1L);
        entity.setUserId(USER_ID);
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.verifyEmail(rawToken));

        assertEquals("This verification link has expired. Please request a new one.", ex.getMessage());
        verify(tokenRepository).delete(entity);
        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyEmailMarksUserVerifiedAndConsumesTokenWhenValid() {
        String rawToken = "raw-token-value";
        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
        entity.setId(1L);
        entity.setUserId(USER_ID);
        entity.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(entity));
        UserEntity user = user(USER_ID, "user@example.com", "Jane Doe", false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.verifyEmail(rawToken);

        assertTrue(user.isEmailVerified());
        verify(userRepository).save(user);
        verify(tokenRepository).delete(entity);
    }

    @Test
    void verifyEmailThrowsWhenUnderlyingUserNoLongerExists() {
        String rawToken = "raw-token-value";
        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
        entity.setId(1L);
        entity.setUserId(USER_ID);
        entity.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(entity));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.verifyEmail(rawToken));

        verify(tokenRepository, never()).delete(entity);
    }

    // ================================================================================
    // resendVerificationEmail
    // ================================================================================

    @Test
    void resendVerificationEmailThrowsWhenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resendVerificationEmail(USER_ID));

        verifyNoInteractions(tokenRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerificationEmailIsANoOpWhenUserAlreadyVerified() {
        UserEntity user = user(USER_ID, "user@example.com", "Jane Doe", true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.resendVerificationEmail(USER_ID);

        verifyNoInteractions(tokenRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerificationEmailSendsANewEmailWhenUserNotYetVerified() {
        UserEntity user = user(USER_ID, "user@example.com", "Jane Doe", false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.resendVerificationEmail(USER_ID);

        verify(tokenRepository).deleteByUserId(USER_ID);
        verify(tokenRepository).save(org.mockito.ArgumentMatchers.any(EmailVerificationTokenEntity.class));
        verify(emailService).sendVerificationEmail(eq("user@example.com"), eq("Jane Doe"), anyString());
    }
}
