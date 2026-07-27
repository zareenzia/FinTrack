package org.example.finzin.service;

import org.example.finzin.entity.PasswordResetTokenEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.PasswordResetTokenRepository;
import org.example.finzin.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long TOKEN_VALIDITY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final AuthService authService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    // No hard-coded default here on purpose — an empty value just means reset links come out
    // broken (logged below) rather than the whole app failing to start over a missing placeholder.
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    @PostConstruct
    void checkConfigured() {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            log.warn("app.frontend.base-url is not set — password reset links will be missing their domain until it is configured.");
        }
    }

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                 AuthService authService, EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.authService = authService;
        this.emailService = emailService;
    }

    /** Silently no-ops if the email doesn't match an account — callers must respond generically either way. */
    public void requestReset(String email) {
        Optional<UserEntity> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) return;
        UserEntity user = userOpt.get();

        tokenRepository.deleteByUserId(user.getId());

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);

        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setUserId(user.getId());
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES));
        tokenRepository.save(entity);

        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
    }

    public void resetPassword(String rawToken, String newPassword) throws IllegalArgumentException {
        PasswordResetTokenEntity entity = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has already been used."));

        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(entity);
            throw new IllegalArgumentException("This reset link has expired. Please request a new one.");
        }

        authService.resetPassword(entity.getUserId(), newPassword);
        tokenRepository.delete(entity);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
