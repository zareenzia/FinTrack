package org.example.finzin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's auto-configured ObjectMapper is the new Jackson 3 (tools.jackson.*) type.
 * This app (and jjwt-jackson) still uses classic Jackson 2 (com.fasterxml.jackson.*), so we
 * expose that type explicitly for anything that needs it injected (e.g. the AI module).
 */
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
