package org.example.finzin.web;

import org.example.finzin.entity.SidebarPreferenceEntity;
import org.example.finzin.repository.SidebarPreferenceRepository;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SidebarPreferenceApiController.class)
class SidebarPreferenceApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SidebarPreferenceRepository repository;

    @Test
    void getPreferencesReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/sidebar-preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void getPreferencesReturnsDefaultEmptyArrayWhenNoneSaved() throws Exception {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sidebar-preferences").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferencesJson").value("[]"));
    }

    @Test
    void getPreferencesReturnsSavedJsonWhenPresent() throws Exception {
        SidebarPreferenceEntity entity = new SidebarPreferenceEntity();
        entity.setUserId(USER_ID);
        entity.setPreferencesJson("[{\"id\":\"dashboard\",\"visible\":true}]");
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/sidebar-preferences").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferencesJson").value("[{\"id\":\"dashboard\",\"visible\":true}]"));
    }

    @Test
    void getPreferencesFallsBackToEmptyArrayWhenStoredJsonIsNull() throws Exception {
        SidebarPreferenceEntity entity = new SidebarPreferenceEntity();
        entity.setUserId(USER_ID);
        entity.setPreferencesJson(null);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/sidebar-preferences").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferencesJson").value("[]"));
    }

    @Test
    void savePreferencesReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/sidebar-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferencesJson\":\"[]\"}"))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).save(any());
    }

    @Test
    void savePreferencesCreatesNewEntityAndDefaultsMissingJsonToEmptyArray() throws Exception {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(SidebarPreferenceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/sidebar-preferences")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("saved"));

        verify(repository).save(argThat((SidebarPreferenceEntity e) ->
                USER_ID.equals(e.getUserId()) && "[]".equals(e.getPreferencesJson())));
    }

    @Test
    void savePreferencesUpdatesExistingEntityWithProvidedJson() throws Exception {
        SidebarPreferenceEntity existing = new SidebarPreferenceEntity();
        existing.setUserId(USER_ID);
        existing.setPreferencesJson("[]");
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(SidebarPreferenceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/sidebar-preferences")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferencesJson\":\"[{\\\"id\\\":\\\"notes\\\"}]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("saved"));

        verify(repository).save(argThat((SidebarPreferenceEntity e) ->
                "[{\"id\":\"notes\"}]".equals(e.getPreferencesJson())));
    }

    @Test
    void resetPreferencesReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(delete("/api/sidebar-preferences"))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).delete(any());
    }

    @Test
    void resetPreferencesDeletesExistingEntity() throws Exception {
        SidebarPreferenceEntity existing = new SidebarPreferenceEntity();
        existing.setUserId(USER_ID);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/sidebar-preferences").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reset"));

        verify(repository, times(1)).delete(existing);
    }

    @Test
    void resetPreferencesIsANoOpWhenNothingIsSaved() throws Exception {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/sidebar-preferences").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reset"));

        verify(repository, never()).delete(any());
    }
}
