package org.example.finzin.config;

import jakarta.servlet.FilterChain;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.EmailService;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers JwtAuthFilter's "must verify email before mutating anything" gate: mutating requests
 * (POST/PUT/PATCH/DELETE) from an authenticated-but-unverified user are rejected with 403 — but
 * only once outbound mail is actually configured (EmailService.isConfigured()), so that new
 * registrations aren't locked out forever when SMTP hasn't been set up yet. Read-only (GET)
 * requests, public routes, and unauthenticated/invalid-token requests are unaffected either way.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final Long USER_ID = 7L;
    private static final String TOKEN = "valid.jwt.token";

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtTokenProvider, eventPublisher, userRepository, emailService);
    }

    private UserEntity user(boolean verified) {
        UserEntity u = new UserEntity();
        u.setId(USER_ID);
        u.setEmail("user@example.com");
        u.setEmailVerified(verified);
        return u;
    }

    private void authenticated() {
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        return req;
    }

    @Test
    void publicAuthRouteSkipsTokenHandlingEntirely() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertEquals(200, res.getStatus());
        org.mockito.Mockito.verifyNoInteractions(jwtTokenProvider, userRepository, emailService);
    }

    @Test
    void mutatingRequestWithoutTokenPassesThroughUnaffected() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/transactions");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertNull(req.getAttribute("userId"));
    }

    @Test
    void getRequestWithValidTokenSetsUserIdAndIsNeverBlockedRegardlessOfVerification() throws Exception {
        // GET is not in MUTATING_METHODS, so the verification check (and its emailService/
        // userRepository lookups) is never reached for read-only requests.
        authenticated();

        MockHttpServletRequest req = request("GET", "/api/transactions");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertEquals(USER_ID, req.getAttribute("userId"));
        assertEquals(200, res.getStatus());
        org.mockito.Mockito.verifyNoInteractions(emailService, userRepository);
    }

    @Test
    void mutatingRequestBlockedWhenUserUnverifiedAndMailIsConfigured() throws Exception {
        authenticated();
        when(emailService.isConfigured()).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(false)));

        MockHttpServletRequest req = request("POST", "/api/transactions");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(req, res);
        assertEquals(403, res.getStatus());
        assertEquals(true, res.getContentAsString().contains("Please verify your email to continue"));
    }

    @Test
    void mutatingRequestAllowedWhenUserUnverifiedButMailIsNotConfigured() throws Exception {
        // Critical safety net: without this, newly-registered users would be permanently locked
        // out of every write action in environments where SMTP hasn't been set up yet.
        authenticated();
        when(emailService.isConfigured()).thenReturn(false);

        MockHttpServletRequest req = request("POST", "/api/transactions");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertEquals(200, res.getStatus());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void mutatingRequestAllowedWhenUserIsVerified() throws Exception {
        authenticated();
        when(emailService.isConfigured()).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true)));

        MockHttpServletRequest req = request("PUT", "/api/accounts/1");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertEquals(200, res.getStatus());
    }

    @Test
    void mutatingRequestAllowedWhenUserNoLongerExists() throws Exception {
        authenticated();
        when(emailService.isConfigured()).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        MockHttpServletRequest req = request("DELETE", "/api/accounts/1");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertEquals(200, res.getStatus());
    }

    @Test
    void invalidTokenPassesThroughWithoutSettingUserIdOrBlocking() throws Exception {
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(false);

        MockHttpServletRequest req = request("POST", "/api/transactions");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertNull(req.getAttribute("userId"));
        org.mockito.Mockito.verifyNoInteractions(userRepository, emailService);
    }
}
