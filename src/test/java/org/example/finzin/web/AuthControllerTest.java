package org.example.finzin.web;

import org.example.finzin.entity.UserEntity;
import org.example.finzin.service.AccountDeletionService;
import org.example.finzin.service.AuthService;
import org.example.finzin.service.BudgetScheduler;
import org.example.finzin.service.EmailVerificationService;
import org.example.finzin.service.JwtTokenProvider;
import org.example.finzin.service.PasswordResetService;
import org.example.finzin.service.RecurringTransactionExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController is security-critical (register/login/logout-cookie/forgot+reset password), so
 * this covers registration validation (including duplicate username/email surfaced as
 * IllegalArgumentException from AuthService), login success/failure, the Authorization
 * header/token flow used by every other endpoint here, and the "never leak whether an email is
 * registered" behavior of forgot-password.
 *
 * Note: unlike every other controller in this batch, AuthController does NOT read the
 * request-attribute "userId" that JwtAuthFilter sets — it re-derives the user itself from the raw
 * {@code Authorization} header via the injected {@link JwtTokenProvider}, independently of the
 * filter. So "authenticated" here means stubbing {@code jwtTokenProvider.validateToken/extractUserId}
 * and sending a real {@code Authorization: Bearer <token>} header, not a {@code .requestAttr(...)}.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    private static final Long USER_ID = 42L;
    private static final String TOKEN = "valid.jwt.token";

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void uploadDirProperty(DynamicPropertyRegistry registry) {
        // Redirects AuthController's @Value("${app.upload.dir:...}") profile-picture writes into a
        // disposable JUnit temp directory instead of the real "user-uploads/profiles" on disk.
        registry.add("app.upload.dir", () -> uploadDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RecurringTransactionExecutionService recurringTransactionExecutionService;

    @MockitoBean
    private BudgetScheduler budgetScheduler;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    private UserEntity user() {
        UserEntity u = new UserEntity();
        u.setId(USER_ID);
        u.setFullName("Leah Rahman");
        u.setUsername("leah");
        u.setEmail("leah@example.com");
        u.setPasswordHash("hashed");
        u.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        u.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return u;
    }

    private void authenticated() {
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    // ══════════════════ POST /api/auth/register ══════════════════

    @Test
    void registerReturnsBadRequestWhenFullNameMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"leah\",\"email\":\"leah@example.com\",\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Full name is required"));
    }

    @Test
    void registerReturnsBadRequestWhenUsernameMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"email\":\"leah@example.com\",\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username is required"));
    }

    @Test
    void registerReturnsBadRequestWhenEmailMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"username\":\"leah\",\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email is required"));
    }

    @Test
    void registerReturnsBadRequestWhenPasswordMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"username\":\"leah\",\"email\":\"leah@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password is required"));
    }

    @Test
    void registerReturnsBadRequestWhenPasswordsDoNotMatch() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"username\":\"leah\",\"email\":\"leah@example.com\"," +
                                "\"password\":\"Passw0rd!\",\"confirmPassword\":\"Different1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Passwords do not match"));

        verify(authService, never()).register(any(), any(), any(), any());
    }

    @Test
    void registerReturnsBadRequestWhenUsernameAlreadyExists() throws Exception {
        when(authService.register("Leah", "leah", "leah@example.com", "Passw0rd!"))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"username\":\"leah\",\"email\":\"leah@example.com\"," +
                                "\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists"));
    }

    @Test
    void registerReturnsBadRequestWhenEmailAlreadyExists() throws Exception {
        when(authService.register("Leah", "leah", "leah@example.com", "Passw0rd!"))
                .thenThrow(new IllegalArgumentException("Email already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"username\":\"leah\",\"email\":\"leah@example.com\"," +
                                "\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already exists"));
    }

    @Test
    void registerReturns500WhenAuthServiceThrowsUnexpectedException() throws Exception {
        when(authService.register(any(), any(), any(), any())).thenThrow(new RuntimeException("DB down"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"username\":\"leah\",\"email\":\"leah@example.com\"," +
                                "\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void registerReturnsCreatedWithTokenAndCookieOnSuccess() throws Exception {
        UserEntity registered = user();
        when(authService.register("Leah", "leah", "leah@example.com", "Passw0rd!")).thenReturn(registered);
        when(jwtTokenProvider.generateToken(registered)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Leah\",\"username\":\"leah\",\"email\":\"leah@example.com\"," +
                                "\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.user.id").value(USER_ID))
                .andExpect(jsonPath("$.user.username").value("leah"))
                .andExpect(jsonPath("$.user.email").value("leah@example.com"))
                .andExpect(cookie().value("Authorization", TOKEN))
                .andExpect(cookie().maxAge("Authorization", 7 * 24 * 60 * 60));
    }

    // ══════════════════ POST /api/auth/register-simple ══════════════════

    @Test
    void registerSimplifiedReturnsBadRequestWhenPasswordsDoNotMatch() throws Exception {
        mockMvc.perform(post("/api/auth/register-simple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leah@example.com\",\"password\":\"Passw0rd!\",\"confirmPassword\":\"Nope1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Passwords do not match"));
    }

    @Test
    void registerSimplifiedReturnsCreatedOnSuccess() throws Exception {
        UserEntity registered = user();
        when(authService.registerSimplified("leah@example.com", "Passw0rd!")).thenReturn(registered);
        when(jwtTokenProvider.generateToken(registered)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/register-simple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leah@example.com\",\"password\":\"Passw0rd!\",\"confirmPassword\":\"Passw0rd!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.user.email").value("leah@example.com"));
    }

    // ══════════════════ POST /api/auth/login ══════════════════

    @Test
    void loginReturnsBadRequestWhenUsernameOrEmailMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username or email is required"));
    }

    @Test
    void loginReturnsBadRequestWhenPasswordMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"leah\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password is required"));
    }

    @Test
    void loginReturnsUnauthorizedWhenCredentialsInvalid() throws Exception {
        when(authService.login("leah", "wrongpass")).thenThrow(new IllegalArgumentException("Invalid password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"leah\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid password"));
    }

    @Test
    void loginReturnsUnauthorizedWhenUserDoesNotExist() throws Exception {
        when(authService.login("ghost", "whatever")).thenThrow(new IllegalArgumentException("Invalid username or email"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or email"));
    }

    @Test
    void loginReturnsOkAndSetsCookieOnSuccessAndTriggersCatchUpJobs() throws Exception {
        UserEntity loggedIn = user();
        when(authService.login("leah", "Passw0rd!")).thenReturn(loggedIn);
        when(jwtTokenProvider.generateToken(loggedIn)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"leah\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.user.username").value("leah"))
                .andExpect(cookie().value("Authorization", TOKEN));

        verify(recurringTransactionExecutionService).processDueForUser(USER_ID);
        verify(budgetScheduler).checkPlansForUser(USER_ID);
    }

    @Test
    void loginStillSucceedsWhenCatchUpJobsThrow() throws Exception {
        UserEntity loggedIn = user();
        when(authService.login("leah", "Passw0rd!")).thenReturn(loggedIn);
        when(jwtTokenProvider.generateToken(loggedIn)).thenReturn(TOKEN);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(recurringTransactionExecutionService).processDueForUser(USER_ID);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(budgetScheduler).checkPlansForUser(USER_ID);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"leah\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN));
    }

    @Test
    void loginIncludesProfilePictureUrlOnlyWhenSet() throws Exception {
        UserEntity loggedIn = user();
        loggedIn.setProfilePicture("abc123.jpg");
        when(authService.login("leah", "Passw0rd!")).thenReturn(loggedIn);
        when(jwtTokenProvider.generateToken(loggedIn)).thenReturn(TOKEN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"leah\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.profilePicture").value("/user-uploads/profiles/abc123.jpg"));
    }

    // ══════════════════ GET /api/auth/me ══════════════════

    @Test
    void getCurrentUserReturnsUnauthorizedWhenNoHeader() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("No token provided or invalid token"));
    }

    @Test
    void getCurrentUserReturnsUnauthorizedWhenTokenInvalid() throws Exception {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUserReturnsNotFoundWhenUserMissing() throws Exception {
        authenticated();
        when(authService.getUserById(USER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    void getCurrentUserReturnsProfileOnSuccess() throws Exception {
        authenticated();
        when(authService.getUserById(USER_ID)).thenReturn(user());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.username").value("leah"))
                .andExpect(jsonPath("$.email").value("leah@example.com"))
                .andExpect(jsonPath("$.profileComplete").value(true));
    }

    // ══════════════════ PUT /api/auth/profile ══════════════════

    @Test
    void updateProfileReturnsUnauthorizedWhenNoHeader() throws Exception {
        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileReturnsBadRequestWhenUsernameTaken() throws Exception {
        authenticated();
        when(authService.updateProfile(eq(USER_ID), any(), eq("taken")))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"taken\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists"));
    }

    @Test
    void updateProfileReturnsOkOnSuccess() throws Exception {
        authenticated();
        UserEntity updated = user();
        updated.setFullName("New Name");
        when(authService.updateProfile(USER_ID, "New Name", null)).thenReturn(updated);

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.fullName").value("New Name"));
    }

    // ══════════════════ GET /api/auth/check-username ══════════════════

    @Test
    void checkUsernameReturnsUnauthorizedWhenNoHeader() throws Exception {
        mockMvc.perform(get("/api/auth/check-username").param("username", "leah"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkUsernameReturnsInvalidFormatForTooShortUsername() throws Exception {
        authenticated();

        mockMvc.perform(get("/api/auth/check-username")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("username", "ab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.error").value("Invalid username format"));

        verify(authService, never()).isUsernameAvailable(any(), any());
    }

    @Test
    void checkUsernameReturnsAvailableTrue() throws Exception {
        authenticated();
        when(authService.isUsernameAvailable("newname", USER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/auth/check-username")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("username", "newname"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void checkUsernameReturnsAvailableFalseWhenTaken() throws Exception {
        authenticated();
        when(authService.isUsernameAvailable("existing", USER_ID)).thenReturn(false);

        mockMvc.perform(get("/api/auth/check-username")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("username", "existing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.error").value("Username is already taken"));
    }

    // ══════════════════ POST /api/auth/change-password ══════════════════

    @Test
    void changePasswordReturnsUnauthorizedWhenNoHeader() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Old1!\",\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"New1!aaaa\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordReturnsBadRequestWhenNewPasswordsDoNotMatch() throws Exception {
        authenticated();

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Old1!\",\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"Different\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Passwords do not match"));

        verify(authService, never()).changePassword(any(), any(), any());
    }

    @Test
    void changePasswordReturnsBadRequestWhenCurrentPasswordWrong() throws Exception {
        authenticated();
        when(authService.changePassword(USER_ID, "WrongOld1!", "New1!aaaa"))
                .thenThrow(new IllegalArgumentException("Current password is incorrect"));

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongOld1!\",\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"New1!aaaa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect"));
    }

    @Test
    void changePasswordReturnsOkOnSuccess() throws Exception {
        authenticated();
        when(authService.changePassword(USER_ID, "Old1!aaaa", "New1!aaaa")).thenReturn(user());

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Old1!aaaa\",\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"New1!aaaa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    // ══════════════════ POST /api/auth/forgot-password ══════════════════

    @Test
    void forgotPasswordReturnsBadRequestWhenEmailMissing() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email is required"));
    }

    @Test
    void forgotPasswordAlwaysReturnsGenericMessageWhenEmailIsUnregistered() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists for that email, a password reset link has been sent."));

        verify(passwordResetService).requestReset("ghost@example.com");
    }

    @Test
    void forgotPasswordReturnsSameGenericMessageEvenWhenServiceThrows() throws Exception {
        // Security-critical: no distinguishable error/response leak whether the email is registered
        // or whether something failed internally — always the same generic 200 message either way.
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down")).when(passwordResetService).requestReset("leah@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leah@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists for that email, a password reset link has been sent."));
    }

    // ══════════════════ POST /api/auth/reset-password ══════════════════

    @Test
    void resetPasswordReturnsBadRequestWhenTokenMissing() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"New1!aaaa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Reset token is required"));
    }

    @Test
    void resetPasswordReturnsBadRequestWhenPasswordsDoNotMatch() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"sometoken\",\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"Different\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Passwords do not match"));
    }

    @Test
    void resetPasswordReturnsBadRequestWhenTokenInvalidOrExpired() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("This reset link has expired. Please request a new one."))
                .when(passwordResetService).resetPassword("expiredtoken", "New1!aaaa");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expiredtoken\",\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"New1!aaaa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This reset link has expired. Please request a new one."));
    }

    @Test
    void resetPasswordReturnsOkOnSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"goodtoken\",\"newPassword\":\"New1!aaaa\",\"confirmPassword\":\"New1!aaaa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successful. You can now log in."));

        verify(passwordResetService).resetPassword("goodtoken", "New1!aaaa");
    }

    // ══════════════════ POST /api/auth/profile-picture ══════════════════

    @Test
    void uploadProfilePictureReturnsUnauthorizedWhenNoHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile("picture", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/auth/profile-picture").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadProfilePictureReturnsBadRequestWhenFileEmpty() throws Exception {
        authenticated();
        MockMultipartFile emptyFile = new MockMultipartFile("picture", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/auth/profile-picture").file(emptyFile)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No file provided"));
    }

    @Test
    void uploadProfilePictureReturnsBadRequestForUnsupportedContentType() throws Exception {
        authenticated();
        MockMultipartFile file = new MockMultipartFile("picture", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/auth/profile-picture").file(file)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported file type. Use JPG, PNG, or WebP."));
    }

    @Test
    void uploadProfilePictureReturnsBadRequestWhenFileTooLarge() throws Exception {
        authenticated();
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("picture", "big.jpg", "image/jpeg", tooBig);

        mockMvc.perform(multipart("/api/auth/profile-picture").file(file)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File too large. Maximum size is 5 MB."));
    }

    @Test
    void uploadProfilePictureReturnsOkAndUpdatesProfilePictureOnSuccess() throws Exception {
        authenticated();
        when(authService.getUserById(USER_ID)).thenReturn(user());
        MockMultipartFile file = new MockMultipartFile("picture", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/auth/profile-picture").file(file)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile picture updated"))
                .andExpect(jsonPath("$.profilePicture").exists());

        verify(authService, times(1)).updateProfilePicture(eq(USER_ID), any());
    }

    // ══════════════════ DELETE /api/auth/profile-picture ══════════════════

    @Test
    void removeProfilePictureReturnsUnauthorizedWhenNoHeader() throws Exception {
        mockMvc.perform(delete("/api/auth/profile-picture"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeProfilePictureReturnsOkOnSuccess() throws Exception {
        authenticated();
        UserEntity existing = user();
        existing.setProfilePicture("old.jpg");
        when(authService.getUserById(USER_ID)).thenReturn(existing);

        mockMvc.perform(delete("/api/auth/profile-picture").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile picture removed"));

        verify(authService).updateProfilePicture(USER_ID, null);
    }

    // ══════════════════ DELETE /api/auth/account ══════════════════

    @Test
    void deleteAccountReturnsUnauthorizedWhenNoHeader() throws Exception {
        mockMvc.perform(delete("/api/auth/account"))
                .andExpect(status().isUnauthorized());

        verify(accountDeletionService, never()).deleteAccount(any());
    }

    @Test
    void deleteAccountReturnsOkOnSuccess() throws Exception {
        authenticated();

        mockMvc.perform(delete("/api/auth/account").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted"));

        verify(accountDeletionService).deleteAccount(USER_ID);
    }
}
