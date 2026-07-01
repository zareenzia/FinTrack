package org.example.finzin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finzin.service.JwtTokenProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    
    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        
        // Skip filter for public routes and static assets
        if (requestURI.equals("/") || 
            requestURI.startsWith("/api/auth/") || 
            requestURI.equals("/login") ||
            requestURI.equals("/login.html") ||
            requestURI.equals("/signup") ||
            requestURI.equals("/signup.html") ||
            requestURI.startsWith("/static/") ||
            requestURI.startsWith("/css/") ||
            requestURI.startsWith("/js/") ||
            requestURI.startsWith("/data/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Try to extract JWT token from Authorization header
        String token = null;
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            System.out.println("✓ Token found in Authorization header for: " + requestURI);
        }
        
        // Also check for token in Authorization cookie (stored without "Bearer " prefix)
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("Authorization".equals(cookie.getName())) {
                    String cookieValue = cookie.getValue();
                    // Cookie stores just the token, no "Bearer " prefix
                    if (!cookieValue.isEmpty()) {
                        token = cookieValue;
                        System.out.println("✓ Token found in Authorization cookie for: " + requestURI);
                    }
                    break;
                }
            }
        }
        
        // Validate token and set userId attribute
        if (token != null) {
            if (jwtTokenProvider.validateToken(token)) {
                Long userId = jwtTokenProvider.extractUserId(token);
                if (userId != null) {
                    request.setAttribute("userId", userId);
                    System.out.println("✓ UserId set to: " + userId + " for: " + requestURI);
                } else {
                    System.out.println("⚠️ Could not extract userId from token for: " + requestURI);
                }
            } else {
                System.out.println("⚠️ Token validation failed for: " + requestURI);
            }
        } else {
            System.out.println("⚠️ No token found in request for: " + requestURI);
        }
        
        filterChain.doFilter(request, response);
    }
}
