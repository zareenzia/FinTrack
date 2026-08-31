package org.example.finzin.web;

import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PageController has no service dependencies — it only branches on the "userId" request attribute
 * that {@code JwtAuthFilter} would have set, and returns a redirect:/forward: view name. No jsonPath
 * assertions here (these aren't JSON endpoints); we assert on the resolved redirect/forward target
 * and status code instead.
 *
 * {@link JwtTokenProvider} is mocked purely so {@code JwtAuthFilter} (a {@code Filter} bean, always
 * picked up by @WebMvcTest's type-based component inclusion) can be constructed — it needs no
 * stubbing since we drive "authenticated" by setting the userId request attribute directly rather
 * than routing through the real filter.
 */
@WebMvcTest(controllers = PageController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // ── "/" root ─────────────────────────────────────────────────────────────

    @Test
    void rootRedirectsToDashboardWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/").requestAttr("userId", 42L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void rootRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── /login and /signup: redirect away if already authenticated, else forward to the static page ──

    @ParameterizedTest
    @CsvSource({
            "/login, /login.html",
            "/signup, /signup.html"
    })
    void loginAndSignupRedirectToDashboardWhenAlreadyAuthenticated(String path, String ignoredTarget) throws Exception {
        mockMvc.perform(get(path).requestAttr("userId", 42L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @ParameterizedTest
    @CsvSource({
            "/login, /login.html",
            "/signup, /signup.html"
    })
    void loginAndSignupForwardToStaticPageWhenNotAuthenticated(String path, String target) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(target));
    }

    // ── /reset-password: always forwards, no auth check at all ──────────────

    @Test
    void resetPasswordAlwaysForwardsRegardlessOfAuth() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/reset-password.html"));

        mockMvc.perform(get("/reset-password").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/reset-password.html"));
    }

    // ── Protected pages: redirect to /login when unauthenticated, forward to their static page otherwise ──

    @ParameterizedTest
    @CsvSource({
            "/dashboard, /dashboard.html",
            "/transactions, /transactions.html",
            "/notes, /notes.html",
            "/todos, /todos.html",
            "/settings, /settings.html",
            "/assets, /assets.html",
            "/budget-planner, /budget-planner.html",
            "/ai-assistant, /ai-assistant.html",
            "/financial-planner, /financial-planner.html",
            "/achievements, /achievements.html",
            "/family-finance, /family-finance.html"
    })
    void protectedPagesRedirectToLoginWhenNotAuthenticated(String path, String ignoredTarget) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @CsvSource({
            "/dashboard, /dashboard.html",
            "/transactions, /transactions.html",
            "/notes, /notes.html",
            "/todos, /todos.html",
            "/settings, /settings.html",
            "/assets, /assets.html",
            "/budget-planner, /budget-planner.html",
            "/ai-assistant, /ai-assistant.html",
            "/financial-planner, /financial-planner.html",
            "/achievements, /achievements.html",
            "/family-finance, /family-finance.html"
    })
    void protectedPagesForwardToStaticPageWhenAuthenticated(String path, String target) throws Exception {
        mockMvc.perform(get(path).requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(target));
    }

    // ── /recurring-transactions: unconditional redirect, no auth check, no dependency on userId ──

    @Test
    void recurringTransactionsAlwaysRedirectsToTransactionsRegardlessOfAuth() throws Exception {
        mockMvc.perform(get("/recurring-transactions"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"));

        mockMvc.perform(get("/recurring-transactions").requestAttr("userId", 42L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"));
    }
}
