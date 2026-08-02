package org.example.finzin.web;

import org.example.finzin.entity.NotificationEntity;
import org.example.finzin.service.JwtTokenProvider;
import org.example.finzin.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * userId defaults to 1L (Leah) when unauthenticated — NotificationApiController never returns
 * 401/403 itself, so the "unauthenticated" cases below assert on that documented fallback rather
 * than a rejection.
 */
@WebMvcTest(NotificationApiController.class)
class NotificationApiControllerTest {

    private static final Long USER_ID = 42L;
    private static final Long DEFAULT_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private NotificationService notificationService;

    private NotificationEntity notification(Long id, boolean read) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(id);
        entity.setUserId(USER_ID);
        entity.setType("BUDGET_EXCEEDED");
        entity.setTitle("Groceries Budget Exceeded");
        entity.setMessage("Groceries is over budget by 500.");
        entity.setRelatedEntityType("BUDGET_CATEGORY");
        entity.setRelatedEntityId(7L);
        entity.setIsRead(read);
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return entity;
    }

    @Test
    void getNotificationsReturnsMappedListForAuthenticatedUser() throws Exception {
        when(notificationService.getForUser(USER_ID)).thenReturn(List.of(notification(1L, false)));

        mockMvc.perform(get("/api/notifications").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$[0].title").value("Groceries Budget Exceeded"))
                .andExpect(jsonPath("$[0].message").value("Groceries is over budget by 500."))
                .andExpect(jsonPath("$[0].relatedEntityType").value("BUDGET_CATEGORY"))
                .andExpect(jsonPath("$[0].relatedEntityId").value(7))
                .andExpect(jsonPath("$[0].isRead").value(false));
    }

    @Test
    void getNotificationsUsesDefaultUserWhenNotAuthenticated() throws Exception {
        when(notificationService.getForUser(DEFAULT_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(notificationService).getForUser(DEFAULT_USER_ID);
    }

    @Test
    void getUnreadCountReturnsCountFromService() throws Exception {
        when(notificationService.unreadCount(USER_ID)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void markReadReturns404WhenServiceReturnsNull() throws Exception {
        when(notificationService.markRead(99L, USER_ID)).thenReturn(null);

        mockMvc.perform(patch("/api/notifications/99/read").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Notification not found"));
    }

    @Test
    void markReadReturnsUpdatedNotificationOnSuccess() throws Exception {
        when(notificationService.markRead(1L, USER_ID)).thenReturn(notification(1L, true));

        mockMvc.perform(patch("/api/notifications/1/read").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isRead").value(true));

        verify(notificationService, times(1)).markRead(eq(1L), eq(USER_ID));
    }

    @Test
    void markAllReadReturnsUpdatedCount() throws Exception {
        when(notificationService.markAllRead(USER_ID)).thenReturn(5);

        mockMvc.perform(patch("/api/notifications/read-all").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(5));
    }
}
