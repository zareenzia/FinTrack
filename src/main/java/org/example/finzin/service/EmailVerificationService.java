package org.example.finzin.service;

import org.example.finzin.entity.EmailVerificationTokenEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.EmailVerificationTokenRepository;
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

@Service
public class EmailVerificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final long TOKEN_VALIDITY_HOURS = 24;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    // No hard-coded default here on purpose — an empty value just means verification links come
    // out broken (logged below) rather than the whole app failing to start over a missing placeholder.
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    @PostConstruct
    void checkConfigured() {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            log.warn("app.frontend.base-url is not set — verification links will be missing their domain until it is configured.");
        }
    }

    public EmailVerificationService(UserRepository userRepository, EmailVerificationTokenRepository tokenRepository,
                                     EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    public void sendVerificationEmail(UserEntity user) {
        tokenRepository.deleteByUserId(user.getId());

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);

        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
        entity.setUserId(user.getId());
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_VALIDITY_HOURS));
        tokenRepository.save(entity);

        String verifyLink = frontendBaseUrl + "/verify-email?token=" + rawToken;
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verifyLink);
    }

    public void verifyEmail(String rawToken) throws IllegalArgumentException {
        EmailVerificationTokenEntity entity = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("This verification link is invalid or has already been used."));

        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(entity);
            throw new IllegalArgumentException("This verification link has expired. Please request a new one.");
        }

        UserEntity user = userRepository.findById(entity.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("This verification link is invalid or has already been used."));
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.delete(entity);
    }

    /** No-op (does not re-send) if the user is already verified. */
    public void resendVerificationEmail(Long userId) throws IllegalArgumentException {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (user.isEmailVerified()) return;
        sendVerificationEmail(user);
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
