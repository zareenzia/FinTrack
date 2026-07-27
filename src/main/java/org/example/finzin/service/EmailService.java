package org.example.finzin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "there";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your FinTrack password");
        message.setText(
            "Hi " + greetingName + ",\n\n" +
            "We received a request to reset your FinTrack password. Click the link below to choose a new one:\n\n" +
            resetLink + "\n\n" +
            "This link expires in 30 minutes. If you didn't request this, you can safely ignore this email.\n\n" +
            "- FinTrack"
        );

        mailSender.send(message);
    }
}
