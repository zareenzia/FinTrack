package org.example.finzin.receipts;

import org.example.finzin.entity.ReceiptSettingsEntity;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for {@link ReceiptSettingsApiController}. */
@WebMvcTest(ReceiptSettingsApiController.class)
class ReceiptSettingsApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider; // only needed so JwtAuthFilter can be constructed
    @MockitoBean private ReceiptSettingsService settingsService;

    private ReceiptSettingsEntity settings(boolean enabled) {
        ReceiptSettingsEntity e = new ReceiptSettingsEntity();
        e.setUserId(USER_ID);
        e.setEnabled(enabled);
        return e;
    }

    @Test
    void getSettings_returnsMappedSettings() throws Exception {
        given(settingsService.getOrDefault(USER_ID)).willReturn(settings(true));

        mockMvc.perform(get("/api/receipts/settings").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void getSettings_defaultsToUserOne_whenUnauthenticated() throws Exception {
        given(settingsService.getOrDefault(1L)).willReturn(settings(true));

        mockMvc.perform(get("/api/receipts/settings"))
                .andExpect(status().isOk());

        verify(settingsService).getOrDefault(1L);
    }

    @Test
    void updateSettings_passesEnabledValueThrough() throws Exception {
        given(settingsService.update(USER_ID, false)).willReturn(settings(false));

        mockMvc.perform(put("/api/receipts/settings").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(settingsService).update(USER_ID, false);
    }

    @Test
    void updateSettings_returnsBadRequest_whenBodyNull() throws Exception {
        // A literal JSON "null" body never reaches the controller's own `body == null` fallback:
        // Spring rejects a null-deserialized non-optional @RequestBody with
        // HttpMessageNotReadableException before the handler method runs, so the response is a
        // framework-generated 400 (not the 200-with-unchanged-settings the controller's own code would
        // produce) and the service is never invoked.
        mockMvc.perform(put("/api/receipts/settings").requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(settingsService);
    }
}
