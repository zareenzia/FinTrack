package org.example.finzin.service;

import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$");
    private static final Pattern USERNAME_PATTERN = 
        Pattern.compile("^[A-Za-z0-9_.]{3,30}$");

    public AuthService(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public UserEntity register(String fullName, String username, String email, String password) 
            throws IllegalArgumentException {
        
        // Validate inputs
        if (username == null || username.length() < 3 || username.length() > 30) {
            throw new IllegalArgumentException("Username must be between 3 and 30 characters");
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters with uppercase, lowercase, number, and special character");
        }
        
        // Check uniqueness
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        // Create and save user
        UserEntity user = new UserEntity();
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordService.hashPassword(password));
        
        return userRepository.save(user);
    }

    public UserEntity registerSimplified(String email, String password) 
            throws IllegalArgumentException {
        
        // Validate inputs
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters with uppercase, lowercase, number, and special character");
        }
        
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        // Derive fullName and username from email (part before @)
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        // Make username unique by appending a random suffix if needed
        String baseUsername = localPart.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
        if (baseUsername.isEmpty()) baseUsername = "user";
        String username = baseUsername;
        int attempt = 0;
        while (userRepository.existsByUsernameIgnoreCase(username)) {
            attempt++;
            username = baseUsername + attempt;
        }
        // Full name: capitalize each word segment split by dots/underscores/hyphens
        String fullName = java.util.Arrays.stream(localPart.split("[._\\-]"))
            .filter(s -> !s.isEmpty())
            .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
            .collect(java.util.stream.Collectors.joining(" "));
        if (fullName.isBlank()) fullName = username;

        // Create and save user
        UserEntity user = new UserEntity();
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordService.hashPassword(password));
        
        return userRepository.save(user);
    }

    public UserEntity updateProfile(Long userId, String fullName, String username) 
            throws IllegalArgumentException {
        
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) throw new IllegalArgumentException("User not found");
        UserEntity user = userOpt.get();

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }

        if (username != null && !username.isBlank()) {
            if (!USERNAME_PATTERN.matcher(username).matches()) {
                throw new IllegalArgumentException("Username must be 3-30 characters, letters/numbers/_/. only");
            }
            Optional<UserEntity> existingUser = userRepository.findByUsernameIgnoreCase(username);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                throw new IllegalArgumentException("Username already exists");
            }
            user.setUsername(username.trim());
        }

        return userRepository.save(user);
    }

    public UserEntity changePassword(Long userId, String currentPassword, String newPassword)
            throws IllegalArgumentException {
        
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) throw new IllegalArgumentException("User not found");
        
        UserEntity user = userOpt.get();
        if (!passwordService.verifyPassword(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character");
        }
        
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        
        user.setPasswordHash(passwordService.hashPassword(newPassword));
        return userRepository.save(user);
    }

    public boolean isUsernameAvailable(String username, Long excludeUserId) {
        Optional<UserEntity> existing = userRepository.findByUsernameIgnoreCase(username);
        if (existing.isEmpty()) return true;
        return existing.get().getId().equals(excludeUserId);
    }

    public UserEntity updateProfilePicture(Long userId, String profilePicture) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) throw new IllegalArgumentException("User not found");
        UserEntity user = userOpt.get();
        user.setProfilePicture(profilePicture);
        return userRepository.save(user);
    }

    public UserEntity login(String username, String password) throws IllegalArgumentException {
        Optional<UserEntity> user = userRepository.findByUsernameOrEmail(username, username);
        
        if (user.isEmpty()) {
            throw new IllegalArgumentException("Invalid username or email");
        }
        
        UserEntity userEntity = user.get();
        if (!passwordService.verifyPassword(password, userEntity.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
        }
        
        return userEntity;
    }

    public UserEntity getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }
}

