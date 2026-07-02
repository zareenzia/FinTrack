package org.example.finzin.web;

import jakarta.servlet.http.HttpServletResponse;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.service.AuthService;
import org.example.finzin.service.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    
    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletResponse httpResponse) {
        try {
            System.out.println("📝 Registration request received for: " + request.getUsername());
            
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
            
            System.out.println("✓ Basic validation passed");
            UserEntity user = authService.register(
                request.getFullName(),
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
            );
            System.out.println("✓ User registered: " + user.getId());
            
            String token = jwtTokenProvider.generateToken(user);
            System.out.println("✓ Token generated");
            
            // Set token as cookie (without "Bearer " prefix since we'll add it in the filter)
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("Authorization", token);
            cookie.setHttpOnly(false);  // Allow JavaScript access for localStorage too
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);  // 7 days
            cookie.setSecure(false);  // Set to true in production with HTTPS
            httpResponse.addCookie(cookie);
            System.out.println("✓ Authorization cookie set");
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "username", user.getUsername(),
                "email", user.getEmail()
            ));
            
            System.out.println("✅ Registration successful");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            System.out.println("⚠️ Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/register-simple")
    public ResponseEntity<?> registerSimplified(@RequestBody SimplifiedRegisterRequest request, HttpServletResponse httpResponse) {
        try {
            System.out.println("📝 Simplified registration request received for: " + request.getEmail());
            
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
            }
            
            System.out.println("✓ Basic validation passed");
            UserEntity user = authService.registerSimplified(
                request.getEmail(),
                request.getPassword()
            );
            System.out.println("✓ User registered: " + user.getId());
            
            String token = jwtTokenProvider.generateToken(user);
            System.out.println("✓ Token generated");
            
            // Set token as cookie
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("Authorization", token);
            cookie.setHttpOnly(false);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setSecure(false);
            httpResponse.addCookie(cookie);
            System.out.println("✓ Authorization cookie set");
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "username", user.getUsername(),
                "email", user.getEmail()
            ));
            
            System.out.println("✅ Simplified registration successful");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            System.out.println("⚠️ Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse httpResponse) {
        try {
            System.out.println("🔐 Login request received for: " + request.getUsernameOrEmail());
            
            if (request.getUsernameOrEmail() == null || request.getUsernameOrEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username or email is required"));
            }
            
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            UserEntity user = authService.login(request.getUsernameOrEmail(), request.getPassword());
            System.out.println("✓ User authenticated: " + user.getId());
            
            String token = jwtTokenProvider.generateToken(user);
            System.out.println("✓ Token generated");
            
            // Set token as cookie (without "Bearer " prefix since we'll add it in the filter)
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("Authorization", token);
            cookie.setHttpOnly(false);  // Allow JavaScript access for localStorage too
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);  // 7 days
            cookie.setSecure(false);  // Set to true in production with HTTPS
            httpResponse.addCookie(cookie);
            System.out.println("✓ Authorization cookie set");
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "username", user.getUsername(),
                "email", user.getEmail()
            ));
            
            System.out.println("✅ Login successful");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            System.out.println("⚠️ Auth error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Login error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No token provided"));
            }
            
            String token = authHeader.substring(7);
            Long userId = jwtTokenProvider.extractUserId(token);
            
            if (userId == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
            }
            
            // Fetch user to get full information
            var user = authService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }
            
            boolean profileComplete = user.getFullName() != null && user.getUsername() != null;
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("fullName", user.getFullName());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("profileComplete", profileComplete);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
        }
    }
    
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader(value = "Authorization", required = false) String authHeader, 
                                          @RequestBody ProfileUpdateRequest request) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No token provided"));
            }
            
            String token = authHeader.substring(7);
            Long userId = jwtTokenProvider.extractUserId(token);
            
            if (userId == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
            }
            
            System.out.println("📝 Profile update request received for user: " + userId);
            
            UserEntity user = authService.updateProfile(userId, request.getFullName(), request.getUsername());
            System.out.println("✓ Profile updated: " + user.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("user", Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "username", user.getUsername(),
                "email", user.getEmail()
            ));
            
            System.out.println("✅ Profile update successful");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            System.out.println("⚠️ Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Profile update error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Profile update failed: " + e.getMessage()));
        }
    }
}
