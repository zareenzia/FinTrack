package org.example.finzin.service;

import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.repository.RecurringTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for RecurringTransactionExecutionService. Per its class Javadoc, this
 * service deliberately never posts transactions or advances nextExecutionDate itself — it only
 * fires "due" and "upcoming" reminder notifications, so these tests focus on which recurring
 * transactions get reminded, with what content, and the pure computeNextDate date-math helper.
 */
@ExtendWith(MockitoExtension.class)
class RecurringTransactionExecutionServiceTest {

    private static final Long USER_ID = 3L;

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private NotificationService notificationService;

    private RecurringTransactionExecutionService service;

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionExecutionService(recurringTransactionRepository, notificationService);
    }

    private RecurringTransactionEntity recurring(Long id, Long userId, String name, double amount, LocalDate nextExecutionDate) {
        RecurringTransactionEntity r = new RecurringTransactionEntity();
        r.setId(id);
        r.setUserId(userId);
        r.setTransactionName(name);
        r.setAmount(amount);
        r.setNextExecutionDate(nextExecutionDate);
        r.setStatus("ACTIVE");
        return r;
    }

    // ================================================================================
    // processAllDue
    // ================================================================================

    @Test
    void processAllDueRemindsEveryDueRecurringTransactionAcrossAllUsers() {
        RecurringTransactionEntity r1 = recurring(1L, USER_ID, "Netflix", 15.0, LocalDate.now());
        RecurringTransactionEntity r2 = recurring(2L, 999L, "Rent", 1200.0, LocalDate.now().minusDays(1));
        when(recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), any()))
                .thenReturn(List.of(r1, r2));

        service.processAllDue();

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("RECURRING_PENDING"),
                contains("Netflix"), contains("Netflix"), eq("RECURRING_TRANSACTION"), eq(1L));
        verify(notificationService).createIfNotRecent(eq(999L), eq("RECURRING_PENDING"),
                contains("Rent"), contains("Rent"), eq("RECURRING_TRANSACTION"), eq(2L));
    }

    @Test
    void processAllDueDoesNothingWhenNoneAreDue() {
        when(recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), any()))
                .thenReturn(List.of());

        service.processAllDue();

        verifyNoInteractions(notificationService);
    }

    @Test
    void remindOneMessageIncludesTransactionNameAmountAndDueDate() {
        RecurringTransactionEntity r = recurring(1L, USER_ID, "Netflix", 15.0, LocalDate.of(2026, 7, 20));
        when(recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), any()))
                .thenReturn(List.of(r));

        service.processAllDue();

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("RECURRING_PENDING"),
                eq("Netflix needs confirmation"),
                eq("\"Netflix\" (15.0) was due on 2026-07-20. Confirm it if it happened, or skip it, in Recurring Transactions."),
                eq("RECURRING_TRANSACTION"), eq(1L));
    }

    // ================================================================================
    // processDueForUser
    // ================================================================================

    @Test
    void processDueForUserOnlyRemindsThatUsersDueRecurringTransactions() {
        RecurringTransactionEntity r = recurring(1L, USER_ID, "Netflix", 15.0, LocalDate.now());
        when(recurringTransactionRepository.findByUserIdAndStatusAndNextExecutionDateLessThanEqual(eq(USER_ID), eq("ACTIVE"), any()))
                .thenReturn(List.of(r));

        service.processDueForUser(USER_ID);

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("RECURRING_PENDING"),
                anyString(), anyString(), eq("RECURRING_TRANSACTION"), eq(1L));
        verify(recurringTransactionRepository, never()).findByStatusAndNextExecutionDateLessThanEqual(anyString(), any());
    }

    // ================================================================================
    // computeNextDate
    // ================================================================================

    @Test
    void computeNextDateAdvancesByDaysForDaily() {
        assertEquals(LocalDate.of(2026, 7, 5),
                RecurringTransactionExecutionService.computeNextDate(LocalDate.of(2026, 7, 1), "DAILY", 4));
    }

    @Test
    void computeNextDateAdvancesByWeeksForWeekly() {
        assertEquals(LocalDate.of(2026, 7, 15),
                RecurringTransactionExecutionService.computeNextDate(LocalDate.of(2026, 7, 1), "WEEKLY", 2));
    }

    @Test
    void computeNextDateAdvancesByMonthsForMonthly() {
        assertEquals(LocalDate.of(2026, 10, 1),
                RecurringTransactionExecutionService.computeNextDate(LocalDate.of(2026, 7, 1), "MONTHLY", 3));
    }

    @Test
    void computeNextDateAdvancesByThreeMonthsPerIntervalForQuarterly() {
        assertEquals(LocalDate.of(2027, 1, 1),
                RecurringTransactionExecutionService.computeNextDate(LocalDate.of(2026, 7, 1), "QUARTERLY", 2));
    }

    @Test
    void computeNextDateAdvancesByYearsForYearly() {
        assertEquals(LocalDate.of(2028, 7, 1),
                RecurringTransactionExecutionService.computeNextDate(LocalDate.of(2026, 7, 1), "YEARLY", 2));
    }

    @Test
    void computeNextDateFallsBackToMonthsForAnUnknownFrequency() {
        assertEquals(LocalDate.of(2026, 8, 1),
                RecurringTransactionExecutionService.computeNextDate(LocalDate.of(2026, 7, 1), "UNKNOWN", 1));
    }

    // ================================================================================
    // sendUpcomingReminders
    // ================================================================================

    @Test
    void sendUpcomingRemindersSkipsItemsAlreadyDueOrOverdue() {
        RecurringTransactionEntity overdue = recurring(1L, USER_ID, "Overdue Bill", 10.0, LocalDate.now().minusDays(1));
        RecurringTransactionEntity dueToday = recurring(2L, USER_ID, "Today Bill", 10.0, LocalDate.now());
        when(recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), any()))
                .thenReturn(List.of(overdue, dueToday));

        service.sendUpcomingReminders();

        verifyNoInteractions(notificationService);
    }

    @Test
    void sendUpcomingRemindersSaysTomorrowWhenOneDayAway() {
        RecurringTransactionEntity r = recurring(1L, USER_ID, "Netflix", 15.0, LocalDate.now().plusDays(1));
        when(recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), any()))
                .thenReturn(List.of(r));

        service.sendUpcomingReminders();

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("RECURRING_UPCOMING"),
                eq("Netflix is due tomorrow"), contains("tomorrow"), eq("RECURRING_TRANSACTION"), eq(1L));
    }

    @Test
    void sendUpcomingRemindersSaysInNDaysWhenMultipleDaysAway() {
        RecurringTransactionEntity r = recurring(1L, USER_ID, "Netflix", 15.0, LocalDate.now().plusDays(2));
        when(recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), any()))
                .thenReturn(List.of(r));

        service.sendUpcomingReminders();

        verify(notificationService).createIfNotRecent(eq(USER_ID), eq("RECURRING_UPCOMING"),
                eq("Netflix is due in 2 days"), contains("in 2 days"), eq("RECURRING_TRANSACTION"), eq(1L));
    }

    @Test
    void sendUpcomingRemindersQueriesUpToTwoDaysFromToday() {
        when(recurringTransactionRepository.findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), any()))
                .thenReturn(List.of());

        service.sendUpcomingReminders();

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(recurringTransactionRepository).findByStatusAndNextExecutionDateLessThanEqual(eq("ACTIVE"), dateCaptor.capture());
        assertEquals(LocalDate.now().plusDays(2), dateCaptor.getValue());
    }
}
