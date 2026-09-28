package org.example.finzin.service;

import org.example.finzin.entity.NotificationEntity;
import org.example.finzin.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for NotificationService: plain CRUD-ish operations plus the
 * once-per-day de-duplication logic in createIfNotRecent that other services (BudgetPlanService,
 * RecurringTransactionExecutionService) depend on to avoid spamming duplicate notifications.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 5L;

    @Mock private NotificationRepository notificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository);
    }

    private NotificationEntity notification(Long id, Long userId, boolean isRead) {
        NotificationEntity n = new NotificationEntity();
        n.setId(id);
        n.setUserId(userId);
        n.setIsRead(isRead);
        return n;
    }

    // ================================================================================
    // create
    // ================================================================================

    @Test
    void createBuildsUnreadNotificationAndSaves() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationEntity result = service.create(USER_ID, "BUDGET_EXCEEDED", "Title", "Message",
                "BUDGET_CATEGORY", 10L);

        assertEquals(USER_ID, result.getUserId());
        assertEquals("BUDGET_EXCEEDED", result.getType());
        assertEquals("Title", result.getTitle());
        assertEquals("Message", result.getMessage());
        assertEquals("BUDGET_CATEGORY", result.getRelatedEntityType());
        assertEquals(10L, result.getRelatedEntityId());
        assertFalse(result.getIsRead());
    }

    // ================================================================================
    // getForUser / unreadCount
    // ================================================================================

    @Test
    void getForUserDelegatesToRepositoryOrderedByCreatedAtDesc() {
        List<NotificationEntity> list = List.of(notification(1L, USER_ID, false));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(list);

        assertSame(list, service.getForUser(USER_ID));
    }

    @Test
    void unreadCountDelegatesToRepository() {
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(3L);

        assertEquals(3L, service.unreadCount(USER_ID));
    }

    // ================================================================================
    // createIfNotRecent
    // ================================================================================

    @Test
    void createIfNotRecentReturnsFalseAndSkipsCreationWhenAnIdenticalNotificationAlreadyFiredToday() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(USER_ID), eq("BUDGET_EXCEEDED"), eq(10L), any(LocalDateTime.class))).thenReturn(true);

        boolean result = service.createIfNotRecent(USER_ID, "BUDGET_EXCEEDED", "Title", "Message",
                "BUDGET_CATEGORY", 10L);

        assertFalse(result);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createIfNotRecentCreatesAndReturnsTrueWhenNoRecentDuplicateExists() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(USER_ID), eq("BUDGET_EXCEEDED"), eq(10L), any(LocalDateTime.class))).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = service.createIfNotRecent(USER_ID, "BUDGET_EXCEEDED", "Title", "Message",
                "BUDGET_CATEGORY", 10L);

        assertTrue(result);
        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals("BUDGET_EXCEEDED", captor.getValue().getType());
    }

    @Test
    void createIfNotRecentChecksAgainstStartOfTodayAsTheCutoff() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(USER_ID), anyString(), anyLong(), any(LocalDateTime.class))).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createIfNotRecent(USER_ID, "BUDGET_EXCEEDED", "Title", "Message", "BUDGET_CATEGORY", 10L);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).existsByUserIdAndTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(USER_ID), eq("BUDGET_EXCEEDED"), eq(10L), cutoffCaptor.capture());
        assertEquals(LocalDate.now().atStartOfDay(), cutoffCaptor.getValue());
    }

    // ================================================================================
    // markRead
    // ================================================================================

    @Test
    void markReadReturnsNullWhenNotificationNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());

        assertNull(service.markRead(1L, USER_ID));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadReturnsNullWhenNotificationBelongsToAnotherUser() {
        NotificationEntity entity = notification(1L, 999L, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertNull(service.markRead(1L, USER_ID));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadSetsIsReadTrueAndSavesWhenOwned() {
        NotificationEntity entity = notification(1L, USER_ID, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(notificationRepository.save(entity)).thenReturn(entity);

        NotificationEntity result = service.markRead(1L, USER_ID);

        assertTrue(result.getIsRead());
        verify(notificationRepository).save(entity);
    }

    // ================================================================================
    // markAllRead
    // ================================================================================

    @Test
    void markAllReadDelegatesToRepositoryAndReturnsCountFlipped() {
        when(notificationRepository.markAllReadByUserId(USER_ID)).thenReturn(4);

        assertEquals(4, service.markAllRead(USER_ID));
    }
}
