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
        Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,}$");

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

    public UserEntity createDefaultLeahUser() {
        Optional<UserEntity> existing = userRepository.findByUsername("leah");
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        UserEntity leah = new UserEntity();
        leah.setFullName("Leah");
        leah.setUsername("leah");
        leah.setEmail("zareenzia801@gmail.com");
        leah.setPasswordHash(passwordService.hashPassword("Zia@1234"));
        
        return userRepository.save(leah);
    }
}
