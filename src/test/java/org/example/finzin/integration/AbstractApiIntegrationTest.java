package org.example.finzin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.service.gold.GoldPriceScraper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for every cross-module integration test in this package: boots the real Spring
 * context (real controllers, services, JPA/Hibernate, security filter chain) against the
 * disposable Testcontainers Postgres from {@link AbstractIntegrationTest}, wires up MockMvc for
 * real HTTP-shaped requests, and stubs the one external network boundary that fires unconditionally
 * on every application startup regardless of which flow a given test covers: {@link
 * GoldPriceScheduler}'s {@code ApplicationReadyEvent} listener kicks off a background thread that
 * calls the real goldr.org scraper. Mocking {@link GoldPriceScraper} (the interface {@code
 * GoldPriceSyncService} depends on) here means every subclass gets this protection for free instead
 * of needing to remember it per-file.
 *
 * Each test method registers its own user(s) with a random username/email suffix rather than
 * relying on transactional rollback between tests — this avoids fighting the pessimistic
 * row-locking ({@code AccountRepository#findByIdForUpdate}) and {@code @Async} document-indexing
 * paths that a test-level {@code @Transactional} wrapping a real HTTP call would otherwise
 * complicate, while still guaranteeing no two test methods (even across classes sharing a cached
 * context) can collide on a unique username/email.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractApiIntegrationTest extends AbstractIntegrationTest {

    protected static final String VALID_PASSWORD = "Passw0rd!23";
    protected static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected GoldPriceScraper goldPriceScraper;

    @BeforeEach
    void stubGoldPriceScraperAgainstNetworkCalls() throws Exception {
        // Never let GoldPriceScheduler's init-sync thread reach the real goldr.org.
        given(goldPriceScraper.scrapeCurrentPrices()).willReturn(List.of());
    }

    protected String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected record RegisteredUser(Long userId, String token, String username, String email) {
        String authHeader() {
            return "Bearer " + token;
        }
    }

    /** Registers a brand-new user through the real POST /api/auth/register endpoint (so the
     *  returned token is a genuine JwtTokenProvider-signed JWT, exactly as a real client would get
     *  one) and returns its id/token for use as a real Authorization header in later requests. */
    protected RegisteredUser registerUser(String label) throws Exception {
        String suffix = uniqueSuffix();
        String username = (label + suffix).toLowerCase();
        String email = username + "@example.test";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", label + " Test User");
        body.put("username", username);
        body.put("email", email);
        body.put("password", VALID_PASSWORD);
        body.put("confirmPassword", VALID_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Long userId = json.get("user").get("id").asLong();
        String token = json.get("token").asText();
        assertEquals(email, json.get("user").get("email").asText());
        return new RegisteredUser(userId, token, username, email);
    }

    protected JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
