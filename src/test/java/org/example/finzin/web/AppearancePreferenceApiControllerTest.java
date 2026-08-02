package org.example.finzin.web;

import org.example.finzin.entity.AppearancePreferenceEntity;
import org.example.finzin.repository.AppearancePreferenceRepository;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppearancePreferenceApiController.class)
class AppearancePreferenceApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AppearancePreferenceRepository repository;

    @Test
    void getPreferencesReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/appearance-preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void getPreferencesReturnsNullFieldsWhenNoneSaved() throws Exception {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/appearance-preferences").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").doesNotExist())
                .andExpect(jsonPath("$.colorTheme").doesNotExist());
    }

    @Test
    void getPreferencesReturnsSavedThemeAndColorTheme() throws Exception {
        AppearancePreferenceEntity entity = new AppearancePreferenceEntity();
        entity.setUserId(USER_ID);
        entity.setTheme("dark");
        entity.setColorTheme("forest");
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/appearance-preferences").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.colorTheme").value("forest"));
    }

    @Test
    void savePreferencesReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/appearance-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"dark\"}"))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).save(any());
    }

    @Test
    void savePreferencesCreatesNewEntityWhenNoneExists() throws Exception {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(AppearancePreferenceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/appearance-preferences")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"dark\",\"colorTheme\":\"ocean\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("saved"));

        verify(repository).save(argThatMatchesThemeAndColor("dark", "ocean"));
    }

    @Test
    void savePreferencesUpdatesExistingEntityInPlace() throws Exception {
        AppearancePreferenceEntity existing = new AppearancePreferenceEntity();
        existing.setUserId(USER_ID);
        existing.setTheme("light");
        existing.setColorTheme("sunset");
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(AppearancePreferenceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/appearance-preferences")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"dark\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("saved"));

        // Only "theme" was present in the body — colorTheme must be left untouched ("sunset").
        verify(repository).save(argThatMatchesThemeAndColor("dark", "sunset"));
    }

    private AppearancePreferenceEntity argThatMatchesThemeAndColor(String theme, String colorTheme) {
        return org.mockito.ArgumentMatchers.argThat(e ->
                e != null && theme.equals(e.getTheme()) && colorTheme.equals(e.getColorTheme()));
    }
}
