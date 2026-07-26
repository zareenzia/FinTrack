package org.example.finzin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.gamification.GamificationEventType;
import org.example.finzin.service.JwtTokenProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher eventPublisher;

    // Guards the DAILY_ACTIVE publish so it only does real work once per user per day, keeping
    // this cheap on the hot request path — the DB-level xp_history unique constraint is the
    // actual source of truth for dedup (this map isn't persisted, an app restart re-arms it,
    // which is harmless for exactly that reason).
    private final Map<Long, LocalDate> lastActiveDayByUser = new ConcurrentHashMap<>();

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, ApplicationEventPublisher eventPublisher) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.eventPublisher = eventPublisher;
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
            requestURI.startsWith("/user-uploads/") ||
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
                    markDailyActive(userId);
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

    private void markDailyActive(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate previous = lastActiveDayByUser.put(userId, today);
        if (today.equals(previous)) return; // already recorded today, skip without touching the DB
        eventPublisher.publishEvent(new GamificationEvent(userId, GamificationEventType.DAILY_ACTIVE, Map.of()));
    }
}
