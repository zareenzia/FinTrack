package org.example.finzin.service;

import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.RecurringTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the "next run" recalculation used when editing a recurring transaction: it must roll
 * forward from the (possibly just-changed) startDate to the next occurrence on or after today,
 * never leaving a stale/past date, never backfilling, and never re-triggering a period that
 * already executed.
 */
@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private CategoryRepository categoryRepository;

    private RecurringTransactionService service;

    private RecurringTransactionEntity schedule(LocalDate anchor, String frequency, int interval) {
        RecurringTransactionEntity entity = new RecurringTransactionEntity();
        entity.setStartDate(anchor);
        entity.setNextExecutionDate(anchor);
        entity.setFrequency(frequency);
        entity.setIntervalValue(interval);
        return entity;
    }

    @Test
    void monthlyScheduleAnchoredInThePastRollsForwardToOnOrAfterToday() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate today = LocalDate.now();
        LocalDate pastStart = today.minusMonths(2).withDayOfMonth(1);

        LocalDate result = service.nextOccurrenceOnOrAfterToday(schedule(pastStart, "MONTHLY", 1));

        assertFalse(result.isBefore(today), "next run must never be left in the past");
        assertEquals(1, result.getDayOfMonth(), "monthly cadence must preserve the anchor's day-of-month");
    }

    @Test
    void scheduleAnchoredTodayOrLaterIsReturnedUnchanged() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate futureStart = LocalDate.now().plusDays(10);

        LocalDate result = service.nextOccurrenceOnOrAfterToday(schedule(futureStart, "MONTHLY", 1));

        assertEquals(futureStart, result, "a future anchor needs no roll-forward at all");
    }

    @Test
    void weeklyScheduleRollsForwardByExactMultiplesOfTheInterval() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate today = LocalDate.now();
        LocalDate pastStart = today.minusWeeks(5);

        LocalDate result = service.nextOccurrenceOnOrAfterToday(schedule(pastStart, "WEEKLY", 2));

        assertFalse(result.isBefore(today));
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(pastStart, result);
        assertEquals(0, daysBetween % 14, "must land on an exact multiple of the 2-week interval from the anchor");
    }

    // --- recalculateNextExecutionDateForEdit ---------------------------------------------------
    // Used when editing a recurring transaction's startDate/frequency/intervalValue: re-anchors to
    // the (possibly just-changed) startDate, but must never land on-or-before a lastExecutionDate
    // that has already run, and must never be left in the past relative to today.

    @Test
    void neverExecutedEditRollsForwardFromPastStartDateToOnOrAfterToday() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate today = LocalDate.now();
        LocalDate pastStart = today.minusMonths(2).withDayOfMonth(1);

        LocalDate result = service.recalculateNextExecutionDateForEdit(pastStart, "MONTHLY", 1, null);

        assertFalse(result.isBefore(today), "next run must never be left in the past");
        assertEquals(1, result.getDayOfMonth(), "monthly cadence must preserve the anchor's day-of-month");
    }

    @Test
    void alreadyExecutedEditStepsToTheSmallestOccurrenceAfterLastExecutionAndToday() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate today = LocalDate.now();
        // T = smallest day-of-month-1 occurrence that is on or after today.
        LocalDate t = today.getDayOfMonth() == 1 ? today : today.plusMonths(1).withDayOfMonth(1);
        LocalDate startDate = t.minusMonths(2);
        // Clearly after startDate, but off-cadence (not itself a startDate-aligned occurrence).
        LocalDate lastExecutionDate = t.minusMonths(1).plusDays(3);

        LocalDate result = service.recalculateNextExecutionDateForEdit(startDate, "MONTHLY", 1, lastExecutionDate);

        assertTrue(result.isAfter(lastExecutionDate), "must never re-trigger a period that already ran");
        assertFalse(result.isBefore(today), "must never be left showing a stale past date");
        LocalDate oneStepBack = result.minusMonths(1);
        assertFalse(oneStepBack.isAfter(lastExecutionDate),
                "result must be the smallest qualifying occurrence: stepping back one cadence should land at-or-before lastExecutionDate");
    }

    @Test
    void alreadyExecutedEditStepsPastAnExactlyMatchingLastExecutionDate() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate today = LocalDate.now();
        LocalDate t = today.getDayOfMonth() == 1 ? today : today.plusMonths(1).withDayOfMonth(1);
        LocalDate startDate = t.minusMonths(3);
        // Exactly equal to a startDate-aligned occurrence.
        LocalDate lastExecutionDate = t.minusMonths(1);

        LocalDate result = service.recalculateNextExecutionDateForEdit(startDate, "MONTHLY", 1, lastExecutionDate);

        assertTrue(result.isAfter(lastExecutionDate), "a candidate equal to lastExecutionDate must be stepped past, not returned");
        assertFalse(result.isEqual(lastExecutionDate));
        assertFalse(result.isBefore(today));
    }

    @Test
    void weeklyIntervalTwoEditRollsForwardByExactMultiplesOfTheInterval() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(70); // 5 * 14 days back, on the interval-2-week grid
        LocalDate lastExecutionDate = today.minusDays(11); // after startDate, off the 14-day grid

        LocalDate result = service.recalculateNextExecutionDateForEdit(startDate, "WEEKLY", 2, lastExecutionDate);

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, result);
        assertEquals(0, daysBetween % 14, "must land on an exact multiple of the 2-week interval from startDate");
        assertTrue(result.isAfter(lastExecutionDate), "must never re-trigger a period that already ran");
        assertFalse(result.isBefore(today), "must never be left showing a stale past date");
    }

    @Test
    void editWithFutureStartDateAlreadyAfterLastExecutionNeedsNoRollForward() {
        service = new RecurringTransactionService(recurringTransactionRepository, categoryRepository);
        LocalDate futureStart = LocalDate.now().plusDays(10);
        LocalDate lastExecutionDate = LocalDate.now().minusDays(5);

        LocalDate result = service.recalculateNextExecutionDateForEdit(futureStart, "MONTHLY", 1, lastExecutionDate);

        assertEquals(futureStart, result, "a future startDate already past lastExecutionDate needs no roll-forward at all");
    }
}
