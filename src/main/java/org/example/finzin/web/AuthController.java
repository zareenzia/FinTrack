package org.example.finzin.web;

import jakarta.servlet.http.HttpServletResponse;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.service.AuthService;
import org.example.finzin.service.JwtTokenProvider;
import org.example.finzin.service.RecurringTransactionExecutionService;
import org.example.finzin.service.BudgetScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RecurringTransactionExecutionService recurringTransactionExecutionService;
    private final BudgetScheduler budgetScheduler;

    @Value("${app.upload.dir:user-uploads/profiles}")
    private String uploadDir;

    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider,
                           RecurringTransactionExecutionService recurringTransactionExecutionService,
                           BudgetScheduler budgetScheduler) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.recurringTransactionExecutionService = recurringTransactionExecutionService;
        this.budgetScheduler = budgetScheduler;
    }
    
    // --- Helper: extract and validate userId from Authorization header -------
    private Long extractUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) return null;
        return jwtTokenProvider.extractUserId(token);
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletResponse httpResponse) {
        try {
            System.out.println("?? Registration request received for: " + request.getUsername());
            
            if (request.getFullName() == null || request.getFullName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
            }
            
            if (request.getUsername() == null || request.getUsername().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
            }
            
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
            }
            
            System.out.println("? Basic validation passed");
            UserEntity user = authService.register(
                request.getFullName(),
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
            );
            System.out.println("? User registered: " + user.getId());
            
            String token = jwtTokenProvider.generateToken(user);
            System.out.println("? Token generated");
            
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("Authorization", token);
            cookie.setHttpOnly(false);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setSecure(false);
            httpResponse.addCookie(cookie);
            System.out.println("? Authorization cookie set");
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", Map.of(
                "id", user.getId(),
                "fullName", user.getFullName() != null ? user.getFullName() : "",
                "username", user.getUsername() != null ? user.getUsername() : "",
                "email", user.getEmail()
            ));
            
            System.out.println("? Registration successful");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            System.out.println("?? Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("? Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/register-simple")
    public ResponseEntity<?> registerSimplified(@RequestBody SimplifiedRegisterRequest request, HttpServletResponse httpResponse) {
        try {
            System.out.println("?? Simplified registration request received for: " + request.getEmail());
            
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
            }
            
            System.out.println("? Basic validation passed");
            UserEntity user = authService.registerSimplified(
                request.getEmail(),
                request.getPassword()
            );
            System.out.println("? User registered: " + user.getId());
            
            String token = jwtTokenProvider.generateToken(user);
            System.out.println("? Token generated");
            
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("Authorization", token);
            cookie.setHttpOnly(false);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setSecure(false);
            httpResponse.addCookie(cookie);
            System.out.println("? Authorization cookie set");
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", Map.of(
                "id", user.getId(),
                "email", user.getEmail()
            ));
            
            System.out.println("? Simplified registration successful");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            System.out.println("?? Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("? Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse httpResponse) {
        try {
            System.out.println("?? Login request received for: " + request.getUsernameOrEmail());
            
            if (request.getUsernameOrEmail() == null || request.getUsernameOrEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username or email is required"));
            }
            
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            UserEntity user = authService.login(request.getUsernameOrEmail(), request.getPassword());
            System.out.println("? User authenticated: " + user.getId());

            try {
                recurringTransactionExecutionService.processDueForUser(user.getId());
            } catch (Exception e) {
                System.out.println("?? Recurring transaction catch-up failed: " + e.getMessage());
            }
            try {
                budgetScheduler.checkPlansForUser(user.getId());
            } catch (Exception e) {
                System.out.println("?? Budget alert check failed: " + e.getMessage());
            }

            String token = jwtTokenProvider.generateToken(user);
            System.out.println("? Token generated");
            
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("Authorization", token);
            cookie.setHttpOnly(false);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setSecure(false);
            httpResponse.addCookie(cookie);
            System.out.println("? Authorization cookie set");
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("fullName", user.getFullName() != null ? user.getFullName() : "");
            userMap.put("username", user.getUsername() != null ? user.getUsername() : "");
            userMap.put("email", user.getEmail());
            if (user.getProfilePicture() != null) {
                userMap.put("profilePicture", "/user-uploads/profiles/" + user.getProfilePicture());
            }
            response.put("user", userMap);
            
            System.out.println("? Login successful");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            System.out.println("?? Auth error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("? Login error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = extractUserIdFromHeader(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No token provided or invalid token"));
            }
            
            UserEntity user = authService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }
            
            boolean profileComplete = user.getFullName() != null && user.getUsername() != null;
            String picUrl = user.getProfilePicture() != null ? "/user-uploads/profiles/" + user.getProfilePicture() : null;
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("fullName", user.getFullName() != null ? user.getFullName() : "");
            response.put("username", user.getUsername() != null ? user.getUsername() : "");
            response.put("email", user.getEmail());
            response.put("profileComplete", profileComplete);
            response.put("profilePicture", picUrl);
            response.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
        }
    }
    
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader(value = "Authorization", required = false) String authHeader, 
                                          @RequestBody ProfileUpdateRequest request) {
        try {
            Long userId = extractUserIdFromHeader(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No token provided or invalid token"));
            }
            
            System.out.println("?? Profile update request received for user: " + userId);
            
            UserEntity user = authService.updateProfile(userId, request.getFullName(), request.getUsername());
            System.out.println("? Profile updated: " + user.getId());
            
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("fullName", user.getFullName() != null ? user.getFullName() : "");
            userMap.put("username", user.getUsername() != null ? user.getUsername() : "");
            userMap.put("email", user.getEmail());
            if (user.getProfilePicture() != null) {
                userMap.put("profilePicture", "/user-uploads/profiles/" + user.getProfilePicture());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("user", userMap);
            
            System.out.println("? Profile update successful");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            System.out.println("?? Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("? Profile update error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Profile update failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("username") String username) {
        try {
            Long userId = extractUserIdFromHeader(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            
            if (username == null || username.length() < 3 || username.length() > 30 ||
                    !username.matches("^[a-zA-Z0-9_.]+$")) {
                return ResponseEntity.ok(Map.of("available", false, "error", "Invalid username format"));
            }
            
            boolean available = authService.isUsernameAvailable(username, userId);
            if (available) {
                return ResponseEntity.ok(Map.of("available", true));
            } else {
                return ResponseEntity.ok(Map.of("available", false, "error", "Username is already taken"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Check failed"));
        }
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChangePasswordRequest request) {
        try {
            Long userId = extractUserIdFromHeader(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            
            if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
            }
            
            authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to change password"));
        }
    }
    
    @PostMapping(value = "/profile-picture", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadProfilePicture(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("picture") MultipartFile file) {
        try {
            Long userId = extractUserIdFromHeader(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
            }
            
            String contentType = file.getContentType();
            List<String> allowed = Arrays.asList("image/jpeg", "image/png", "image/webp");
            if (contentType == null || !allowed.contains(contentType)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type. Use JPG, PNG, or WebP."));
            }
            
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 5 MB."));
            }
            
            String ext = "jpg";
            if ("image/png".equals(contentType)) ext = "png";
            else if ("image/webp".equals(contentType)) ext = "webp";
            
            String filename = UUID.randomUUID().toString() + "." + ext;
            
            UserEntity existingUser = authService.getUserById(userId);
            if (existingUser != null && existingUser.getProfilePicture() != null) {
                try {
                    Path oldPath = Paths.get(uploadDir).resolve(existingUser.getProfilePicture());
                    Files.deleteIfExists(oldPath);
                } catch (Exception ignored) {}
            }
            
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);
            Path dest = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            
            authService.updateProfilePicture(userId, filename);
            
            String picUrl = "/user-uploads/profiles/" + filename;
            return ResponseEntity.ok(Map.of("profilePicture", picUrl, "message", "Profile picture updated"));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to save file"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Upload failed"));
        }
    }
    
    @DeleteMapping("/profile-picture")
    public ResponseEntity<?> removeProfilePicture(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = extractUserIdFromHeader(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            
            UserEntity user = authService.getUserById(userId);
            if (user != null && user.getProfilePicture() != null) {
                try {
                    Path filePath = Paths.get(uploadDir).resolve(user.getProfilePicture());
                    Files.deleteIfExists(filePath);
                } catch (Exception ignored) {}
                authService.updateProfilePicture(userId, null);
            }
            
            return ResponseEntity.ok(Map.of("message", "Profile picture removed"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to remove picture"));
        }
    }
}
