package org.example.finzin.service;

import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for AuthService, matching BudgetPlanServiceTest's convention. This is
 * the highest-value class in this batch: registration validation, username uniqueness/derivation,
 * profile updates, password change/reset, and login all live here.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String VALID_PASSWORD = "Passw0rd!";
    private static final String OTHER_VALID_PASSWORD = "Different1$";

    @Mock private UserRepository userRepository;
    @Mock private PasswordService passwordService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordService);
    }

    private UserEntity user(Long id, String username, String email, String passwordHash) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(passwordHash);
        return u;
    }

    // ================================================================================
    // register
    // ================================================================================

    @Test
    void registerRejectsUsernameShorterThanThreeCharacters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("Full Name", "ab", "a@b.com", VALID_PASSWORD));
        assertEquals("Username must be between 3 and 30 characters", ex.getMessage());
    }

    @Test
    void registerRejectsUsernameLongerThanThirtyCharacters() {
        String longUsername = "a".repeat(31);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("Full Name", longUsername, "a@b.com", VALID_PASSWORD));
        assertEquals("Username must be between 3 and 30 characters", ex.getMessage());
    }

    @Test
    void registerRejectsNullUsername() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("Full Name", null, "a@b.com", VALID_PASSWORD));
        assertEquals("Username must be between 3 and 30 characters", ex.getMessage());
    }

    @Test
    void registerRejectsInvalidEmailFormat() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("Full Name", "gooduser", "not-an-email", VALID_PASSWORD));
        assertEquals("Invalid email format", ex.getMessage());
    }

    @Test
    void registerRejectsWeakPassword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("Full Name", "gooduser", "a@b.com", "weakpassword"));
        assertEquals("Password must be at least 8 characters with uppercase, lowercase, number, and special character",
                ex.getMessage());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("gooduser")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("Full Name", "gooduser", "a@b.com", VALID_PASSWORD));

        assertEquals("Username already exists", ex.getMessage());
        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsernameIgnoreCase("gooduser")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("Full Name", "gooduser", "a@b.com", VALID_PASSWORD));

        assertEquals("Email already exists", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerSavesHashedPasswordAndReturnsSavedUser() {
        when(userRepository.existsByUsernameIgnoreCase("gooduser")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("a@b.com")).thenReturn(false);
        when(passwordService.hashPassword(VALID_PASSWORD)).thenReturn("hashed-value");
        when(userRepository.save(any())).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserEntity result = service.register("Full Name", "gooduser", "a@b.com", VALID_PASSWORD);

        assertEquals(1L, result.getId());
        assertEquals("Full Name", result.getFullName());
        assertEquals("gooduser", result.getUsername());
        assertEquals("a@b.com", result.getEmail());
        assertEquals("hashed-value", result.getPasswordHash());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed-value", captor.getValue().getPasswordHash());
    }

    // ================================================================================
    // registerSimplified
    // ================================================================================

    @Test
    void registerSimplifiedRejectsInvalidEmailFormat() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.registerSimplified("not-an-email", VALID_PASSWORD));
        assertEquals("Invalid email format", ex.getMessage());
    }

    @Test
    void registerSimplifiedRejectsWeakPassword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.registerSimplified("john.doe@example.com", "weakpassword"));
        assertEquals("Password must be at least 8 characters with uppercase, lowercase, number, and special character",
                ex.getMessage());
    }

    @Test
    void registerSimplifiedRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.registerSimplified("john.doe@example.com", VALID_PASSWORD));

        assertEquals("Email already exists", ex.getMessage());
    }

    @Test
    void registerSimplifiedDerivesUsernameAndFullNameFromLocalPart() {
        when(userRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("johndoe")).thenReturn(false);
        when(passwordService.hashPassword(VALID_PASSWORD)).thenReturn("hashed-value");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserEntity result = service.registerSimplified("john.doe@example.com", VALID_PASSWORD);

        assertEquals("johndoe", result.getUsername(), "dots must be stripped from the derived username");
        assertEquals("John Doe", result.getFullName(), "each dot-separated segment must be capitalized");
        assertEquals("john.doe@example.com", result.getEmail());
        assertEquals("hashed-value", result.getPasswordHash());
    }

    @Test
    void registerSimplifiedAppendsNumericSuffixWhenDerivedUsernameAlreadyTaken() {
        when(userRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("johndoe")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("johndoe1")).thenReturn(false);
        when(passwordService.hashPassword(VALID_PASSWORD)).thenReturn("hashed-value");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserEntity result = service.registerSimplified("john.doe@example.com", VALID_PASSWORD);

        assertEquals("johndoe1", result.getUsername());
    }

    @Test
    void registerSimplifiedFallsBackToUserWhenLocalPartHasNoAlphanumerics() {
        when(userRepository.existsByEmailIgnoreCase("...@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("user")).thenReturn(false);
        when(passwordService.hashPassword(VALID_PASSWORD)).thenReturn("hashed-value");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserEntity result = service.registerSimplified("...@example.com", VALID_PASSWORD);

        assertEquals("user", result.getUsername());
        assertEquals("user", result.getFullName(), "fullName must fall back to the username when no segments remain");
    }

    // ================================================================================
    // updateProfile
    // ================================================================================

    @Test
    void updateProfileThrowsWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile(1L, "New Name", "newuser"));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateProfileUpdatesFullNameOnlyWhenUsernameBlank() {
        UserEntity existing = user(1L, "olduser", "old@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserEntity result = service.updateProfile(1L, "  New Name  ", "  ");

        assertEquals("New Name", result.getFullName());
        assertEquals("olduser", result.getUsername(), "blank username must leave the existing username untouched");
    }

    @Test
    void updateProfileRejectsInvalidUsernamePattern() {
        UserEntity existing = user(1L, "olduser", "old@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile(1L, null, "a"));
        assertEquals("Username must be 3-30 characters, letters/numbers/_/. only", ex.getMessage());
    }

    @Test
    void updateProfileRejectsUsernameAlreadyTakenByAnotherUser() {
        UserEntity existing = user(1L, "olduser", "old@example.com", "hash");
        UserEntity otherUser = user(2L, "newuser", "other@example.com", "hash2");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsernameIgnoreCase("newuser")).thenReturn(Optional.of(otherUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile(1L, null, "newuser"));
        assertEquals("Username already exists", ex.getMessage());
    }

    @Test
    void updateProfileAllowsKeepingYourOwnCurrentUsername() {
        UserEntity existing = user(1L, "sameuser", "old@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsernameIgnoreCase("sameuser")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserEntity result = service.updateProfile(1L, null, "sameuser");

        assertEquals("sameuser", result.getUsername());
    }

    @Test
    void updateProfileUpdatesUsernameWhenAvailable() {
        UserEntity existing = user(1L, "olduser", "old@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsernameIgnoreCase("newuser")).thenReturn(Optional.empty());
        when(userRepository.save(existing)).thenReturn(existing);

        UserEntity result = service.updateProfile(1L, null, "newuser");

        assertEquals("newuser", result.getUsername());
    }

    // ================================================================================
    // changePassword
    // ================================================================================

    @Test
    void changePasswordThrowsWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(1L, "current", VALID_PASSWORD));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordService.verifyPassword("wrong-current", "stored-hash")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(1L, "wrong-current", VALID_PASSWORD));
        assertEquals("Current password is incorrect", ex.getMessage());
    }

    @Test
    void changePasswordRejectsWeakNewPassword() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordService.verifyPassword("current-pass", "stored-hash")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(1L, "current-pass", "weak"));
        assertEquals("Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character",
                ex.getMessage());
    }

    @Test
    void changePasswordRejectsWhenNewPasswordSameAsCurrent() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordService.verifyPassword(VALID_PASSWORD, "stored-hash")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(1L, VALID_PASSWORD, VALID_PASSWORD));
        assertEquals("New password must be different from the current password", ex.getMessage());
    }

    @Test
    void changePasswordHashesAndSavesNewPasswordOnSuccess() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordService.verifyPassword("current-pass", "stored-hash")).thenReturn(true);
        when(passwordService.hashPassword(OTHER_VALID_PASSWORD)).thenReturn("new-hash");
        when(userRepository.save(existing)).thenReturn(existing);

        UserEntity result = service.changePassword(1L, "current-pass", OTHER_VALID_PASSWORD);

        assertEquals("new-hash", result.getPasswordHash());
    }

    // ================================================================================
    // resetPassword
    // ================================================================================

    @Test
    void resetPasswordThrowsWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(1L, VALID_PASSWORD));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(1L, "weak"));
        assertEquals("Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character",
                ex.getMessage());
    }

    @Test
    void resetPasswordHashesAndSavesOnSuccess() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordService.hashPassword(VALID_PASSWORD)).thenReturn("new-hash");
        when(userRepository.save(existing)).thenReturn(existing);

        UserEntity result = service.resetPassword(1L, VALID_PASSWORD);

        assertEquals("new-hash", result.getPasswordHash());
        verify(passwordService, never()).verifyPassword(anyString(), anyString());
    }

    // ================================================================================
    // isUsernameAvailable
    // ================================================================================

    @Test
    void isUsernameAvailableReturnsTrueWhenNoUserHasIt() {
        when(userRepository.findByUsernameIgnoreCase("freeuser")).thenReturn(Optional.empty());

        assertTrue(service.isUsernameAvailable("freeuser", 1L));
    }

    @Test
    void isUsernameAvailableReturnsTrueWhenOnlyTheExcludedUserHasIt() {
        UserEntity existing = user(1L, "sameuser", "u@example.com", "hash");
        when(userRepository.findByUsernameIgnoreCase("sameuser")).thenReturn(Optional.of(existing));

        assertTrue(service.isUsernameAvailable("sameuser", 1L));
    }

    @Test
    void isUsernameAvailableReturnsFalseWhenAnotherUserHasIt() {
        UserEntity existing = user(2L, "takenuser", "u2@example.com", "hash");
        when(userRepository.findByUsernameIgnoreCase("takenuser")).thenReturn(Optional.of(existing));

        assertFalse(service.isUsernameAvailable("takenuser", 1L));
    }

    // ================================================================================
    // updateProfilePicture
    // ================================================================================

    @Test
    void updateProfilePictureThrowsWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateProfilePicture(1L, "pic.png"));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateProfilePictureSetsAndSavesOnSuccess() {
        UserEntity existing = user(1L, "user1", "u@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserEntity result = service.updateProfilePicture(1L, "pic.png");

        assertEquals("pic.png", result.getProfilePicture());
    }

    // ================================================================================
    // login
    // ================================================================================

    @Test
    void loginThrowsWhenUserNotFound() {
        when(userRepository.findByUsernameOrEmail("nouser", "nouser")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("nouser", VALID_PASSWORD));
        assertEquals("Invalid username or email", ex.getMessage());
    }

    @Test
    void loginThrowsWhenPasswordIncorrect() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(existing));
        when(passwordService.verifyPassword("wrong-password", "stored-hash")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login("user1", "wrong-password"));
        assertEquals("Invalid password", ex.getMessage());
    }

    @Test
    void loginReturnsUserOnSuccess() {
        UserEntity existing = user(1L, "user1", "u@example.com", "stored-hash");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(existing));
        when(passwordService.verifyPassword(VALID_PASSWORD, "stored-hash")).thenReturn(true);

        UserEntity result = service.login("user1", VALID_PASSWORD);

        assertSame(existing, result);
    }

    // ================================================================================
    // getUserById
    // ================================================================================

    @Test
    void getUserByIdReturnsUserWhenFound() {
        UserEntity existing = user(1L, "user1", "u@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertSame(existing, service.getUserById(1L));
    }

    @Test
    void getUserByIdReturnsNullWhenNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertNull(service.getUserById(1L));
    }
}
