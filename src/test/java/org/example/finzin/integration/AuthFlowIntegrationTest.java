package org.example.finzin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.finzin.entity.PasswordResetTokenEntity;
import org.example.finzin.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real /api/auth/* controller endpoints end-to-end: register -> duplicate rejection
 * -> login (success/failure) -> a protected endpoint via the returned JWT -> forgot-password ->
 * reset-password, against the real AuthService/PasswordResetService/JwtTokenProvider and a real
 * Postgres instance.
 */
class AuthFlowIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    // Forgot-password (see below) drives the real PasswordResetService, which — when a matching
    // account exists — calls the real EmailService/JavaMailSender. AuthController already swallows
    // any send failure (the response is the same generic message either way, by design — see its
    // javadoc), but application.properties defaults spring.mail.host to smtp.gmail.com with blank
    // credentials, so an unpatched run here would genuinely dial out and wait on real SMTP
    // connect/auth timeouts. Pointing at an unroutable local port instead makes that failure path
    // fail fast (connection refused) so this test stays hermetic and quick.
    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1");
    }

    @Test
    void duplicateUsernameAndEmailAreRejectedOnRegister() throws Exception {
        RegisteredUser user = registerUser("authdup");

        Map<String, Object> duplicateUsername = new LinkedHashMap<>();
        duplicateUsername.put("fullName", "Someone Else");
        duplicateUsername.put("username", user.username());
        duplicateUsername.put("email", "different" + uniqueSuffix() + "@example.test");
        duplicateUsername.put("password", VALID_PASSWORD);
        duplicateUsername.put("confirmPassword", VALID_PASSWORD);

        MvcResult usernameClash = mockMvc.perform(post("/api/auth/register")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(duplicateUsername)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("Username already exists", bodyOf(usernameClash).get("error").asText());

        Map<String, Object> duplicateEmail = new LinkedHashMap<>();
        duplicateEmail.put("fullName", "Someone Else");
        duplicateEmail.put("username", "authdup" + uniqueSuffix());
        duplicateEmail.put("email", user.email());
        duplicateEmail.put("password", VALID_PASSWORD);
        duplicateEmail.put("confirmPassword", VALID_PASSWORD);

        MvcResult emailClash = mockMvc.perform(post("/api/auth/register")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmail)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("Email already exists", bodyOf(emailClash).get("error").asText());
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndFailsOtherwise() throws Exception {
        RegisteredUser user = registerUser("authlogin");

        Map<String, Object> correctLogin = new LinkedHashMap<>();
        correctLogin.put("usernameOrEmail", user.username());
        correctLogin.put("password", VALID_PASSWORD);
        MvcResult okResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(correctLogin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode okBody = bodyOf(okResult);
        assertFalse(okBody.get("token").asText().isBlank());
        assertEquals(user.email(), okBody.get("user").get("email").asText());

        Map<String, Object> wrongPassword = new LinkedHashMap<>();
        wrongPassword.put("usernameOrEmail", user.username());
        wrongPassword.put("password", "TotallyWrong1!");
        MvcResult wrongPasswordResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertEquals("Invalid password", bodyOf(wrongPasswordResult).get("error").asText());

        Map<String, Object> unknownUser = new LinkedHashMap<>();
        unknownUser.put("usernameOrEmail", "nobody" + uniqueSuffix());
        unknownUser.put("password", VALID_PASSWORD);
        MvcResult unknownResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(unknownUser)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertEquals("Invalid username or email", bodyOf(unknownResult).get("error").asText());
    }

    @Test
    void protectedEndpointHonorsRealJwtAndRejectsMissingOrInvalidTokens() throws Exception {
        RegisteredUser user = registerUser("authme");

        MvcResult meResult = mockMvc.perform(get("/api/auth/me").header("Authorization", user.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode me = bodyOf(meResult);
        assertEquals(user.userId(), me.get("userId").asLong());
        assertEquals(user.email(), me.get("email").asText());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordReturnsSameGenericMessageWhetherOrNotEmailExists() throws Exception {
        RegisteredUser user = registerUser("authforgot");

        Map<String, Object> knownEmail = Map.of("email", user.email());
        MvcResult knownResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(knownEmail)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> unknownEmail = Map.of("email", "nobody" + uniqueSuffix() + "@example.test");
        MvcResult unknownResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(unknownEmail)))
                .andExpect(status().isOk())
                .andReturn();

        String knownMessage = bodyOf(knownResult).get("message").asText();
        String unknownMessage = bodyOf(unknownResult).get("message").asText();
        assertEquals(knownMessage, unknownMessage);
        assertTrue(knownMessage.toLowerCase().contains("reset link"));
    }

    @Test
    void resetPasswordWithValidTokenChangesPasswordAndConsumesToken() throws Exception {
        RegisteredUser user = registerUser("authreset");

        // PasswordResetService generates its raw token via SecureRandom internally and only ever
        // persists its SHA-256 hash, so there is no way to recover a genuine raw token issued
        // through the real /forgot-password endpoint. Seeding a token row directly through the same
        // repository the service itself uses — with the identical hashing scheme — exercises the
        // exact same lookup/expiry/consume path the real forgot-password flow would produce.
        String rawToken = "integration-raw-token-" + uniqueSuffix();
        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity();
        tokenEntity.setUserId(user.userId());
        tokenEntity.setTokenHash(sha256Hex(rawToken));
        tokenEntity.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        passwordResetTokenRepository.save(tokenEntity);

        String newPassword = "NewPassw0rd!45";
        Map<String, Object> resetBody = new LinkedHashMap<>();
        resetBody.put("token", rawToken);
        resetBody.put("newPassword", newPassword);
        resetBody.put("confirmPassword", newPassword);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(resetBody)))
                .andExpect(status().isOk());

        // The token is single-use.
        assertTrue(passwordResetTokenRepository.findByTokenHash(sha256Hex(rawToken)).isEmpty());

        Map<String, Object> loginWithOldPassword = new LinkedHashMap<>();
        loginWithOldPassword.put("usernameOrEmail", user.username());
        loginWithOldPassword.put("password", VALID_PASSWORD);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(loginWithOldPassword)))
                .andExpect(status().isUnauthorized());

        Map<String, Object> loginWithNewPassword = new LinkedHashMap<>();
        loginWithNewPassword.put("usernameOrEmail", user.username());
        loginWithNewPassword.put("password", newPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(loginWithNewPassword)))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(user.email(), bodyOf(loginResult).get("user").get("email").asText());
    }

    @Test
    void resetPasswordRejectsInvalidTokenAndMismatchedConfirmation() throws Exception {
        Map<String, Object> invalidToken = new LinkedHashMap<>();
        invalidToken.put("token", "this-token-does-not-exist-" + uniqueSuffix());
        invalidToken.put("newPassword", "SomeNewPass1!");
        invalidToken.put("confirmPassword", "SomeNewPass1!");
        MvcResult invalidResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(invalidToken)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("This reset link is invalid or has already been used.",
                bodyOf(invalidResult).get("error").asText());

        Map<String, Object> mismatched = new LinkedHashMap<>();
        mismatched.put("token", "irrelevant-since-confirmation-is-checked-first");
        mismatched.put("newPassword", "SomeNewPass1!");
        mismatched.put("confirmPassword", "SomethingDifferent1!");
        MvcResult mismatchedResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(mismatched)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("Passwords do not match", bodyOf(mismatchedResult).get("error").asText());
    }

    /** Mirrors PasswordResetService's own private hashing exactly, so a directly-seeded token row
     *  looks up the same way a real requestReset()-generated one would. */
    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes()));
    }
}
