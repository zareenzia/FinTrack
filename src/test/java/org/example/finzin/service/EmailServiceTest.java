package org.example.finzin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Plain-Mockito unit test for EmailService. JavaMailSender is only auto-configured when
 * spring.mail.host is set, which EmailService models with an Optional&lt;JavaMailSender&gt;
 * constructor param — so both the "configured" and "not configured" branches are first-class,
 * testable states rather than edge cases.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;

    /** Sets the private @Value-injected fromAddress field, since we're not booting a Spring context. */
    private void setFromAddress(EmailService service, String value) throws Exception {
        Field field = EmailService.class.getDeclaredField("fromAddress");
        field.setAccessible(true);
        field.set(service, value);
    }

    // ================================================================================
    // isConfigured
    // ================================================================================

    @Test
    void isConfiguredReturnsFalseWhenMailSenderIsAbsent() {
        EmailService service = new EmailService(Optional.empty());

        assertFalse(service.isConfigured());
    }

    @Test
    void isConfiguredReturnsTrueWhenMailSenderIsPresent() {
        EmailService service = new EmailService(Optional.of(mailSender));

        assertTrue(service.isConfigured());
    }

    // ================================================================================
    // sendPasswordResetEmail
    // ================================================================================

    @Test
    void sendPasswordResetEmailThrowsIllegalStateWhenMailSenderNotConfigured() {
        EmailService service = new EmailService(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.sendPasswordResetEmail("user@example.com", "Nabil", "https://app.test/reset?token=abc"));

        assertTrue(ex.getMessage().contains("spring.mail.host"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void sendPasswordResetEmailSendsMessageWithExpectedFieldsWhenConfigured() throws Exception {
        EmailService service = new EmailService(Optional.of(mailSender));
        setFromAddress(service, "TakaFlow <no-reply@takaflow.local>");

        service.sendPasswordResetEmail("user@example.com", "Nabil", "https://app.test/reset-password?token=abc123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();

        assertEquals("TakaFlow <no-reply@takaflow.local>", sent.getFrom());
        assertEquals(1, sent.getTo().length);
        assertEquals("user@example.com", sent.getTo()[0]);
        assertEquals("Reset your TakaFlow password", sent.getSubject());
        assertTrue(sent.getText().contains("Hi Nabil,"));
        assertTrue(sent.getText().contains("https://app.test/reset-password?token=abc123"));
        assertTrue(sent.getText().contains("30 minutes"));
    }

    @Test
    void sendPasswordResetEmailUsesGenericGreetingWhenFullNameIsNull() throws Exception {
        EmailService service = new EmailService(Optional.of(mailSender));
        setFromAddress(service, "no-reply@takaflow.local");

        service.sendPasswordResetEmail("user@example.com", null, "https://app.test/reset?token=xyz");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getText().contains("Hi there,"));
    }

    @Test
    void sendPasswordResetEmailUsesGenericGreetingWhenFullNameIsBlank() throws Exception {
        EmailService service = new EmailService(Optional.of(mailSender));
        setFromAddress(service, "no-reply@takaflow.local");

        service.sendPasswordResetEmail("user@example.com", "   ", "https://app.test/reset?token=xyz");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getText().contains("Hi there,"));
    }
}
