package org.example.finzin.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String fromAddress;

    // JavaMailSender is only auto-configured when spring.mail.host is set; Optional here means
    // a missing mail config degrades this one feature instead of failing the whole app context.
    public EmailService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender.orElse(null);
    }

    @PostConstruct
    void checkConfigured() {
        if (mailSender == null) {
            log.warn("spring.mail.host is not set — password reset emails will fail until it is configured.");
        }
    }

    public boolean isConfigured() {
        return mailSender != null;
    }

    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        if (mailSender == null) {
            throw new IllegalStateException("Email sending is not configured (spring.mail.host is not set).");
        }
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "there";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your TakaFlow password");
        message.setText(
            "Hi " + greetingName + ",\n\n" +
            "We received a request to reset your TakaFlow password. Click the link below to choose a new one:\n\n" +
            resetLink + "\n\n" +
            "This link expires in 30 minutes. If you didn't request this, you can safely ignore this email.\n\n" +
            "- TakaFlow"
        );

        mailSender.send(message);
    }

    public void sendVerificationEmail(String toEmail, String fullName, String verifyLink) {
        if (mailSender == null) {
            throw new IllegalStateException("Email sending is not configured (spring.mail.host is not set).");
        }
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "there";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Verify your TakaFlow email");
        message.setText(
            "Hi " + greetingName + ",\n\n" +
            "Welcome to TakaFlow! Please verify your email address by clicking the link below:\n\n" +
            verifyLink + "\n\n" +
            "This link expires in 24 hours. Until you verify, you can browse your account but can't add or change anything. " +
            "If you didn't create this account, you can safely ignore this email.\n\n" +
            "- TakaFlow"
        );

        mailSender.send(message);
    }
}
