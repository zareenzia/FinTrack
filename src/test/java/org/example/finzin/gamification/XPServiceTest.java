package org.example.finzin.gamification;

import org.example.finzin.entity.UserXpEntity;
import org.example.finzin.repository.UserXpRepository;
import org.example.finzin.repository.XpHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching AIServiceTest's convention) for the two things that must never
 * regress: the (userId, sourceType, sourceId, reason) dedup guard, and the fixed level-threshold scan.
 */
@ExtendWith(MockitoExtension.class)
class XPServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private UserXpRepository userXpRepository;
    @Mock private XpHistoryRepository xpHistoryRepository;

    private XPService xpService;

    @BeforeEach
    void setUp() {
        xpService = new XPService(userXpRepository, xpHistoryRepository);
    }

    @Test
    void awardXpReturnsFalseAndSavesNothingWhenAlreadyAwarded() {
        when(xpHistoryRepository.existsByUserIdAndSourceTypeAndSourceIdAndReason(USER_ID, "TRANSACTION", "5", "expense_logged"))
                .thenReturn(true);

        boolean result = xpService.awardXp(USER_ID, 2, "expense_logged", "TRANSACTION", "5");

        assertFalse(result);
        verify(xpHistoryRepository, never()).save(any());
        verify(userXpRepository, never()).save(any());
    }

    @Test
    void awardXpCreatesUserXpRecordForFirstTimeUser() {
        when(xpHistoryRepository.existsByUserIdAndSourceTypeAndSourceIdAndReason(any(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(userXpRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        // Simulates @PrePersist defaulting, which a mocked save() never actually runs.
        when(userXpRepository.save(any())).thenAnswer(inv -> {
            UserXpEntity e = inv.getArgument(0);
            if (e.getTotalXp() == null) e.setTotalXp(0L);
            if (e.getCurrentLevel() == null) e.setCurrentLevel(1);
            return e;
        });

        boolean result = xpService.awardXp(USER_ID, 10, "note_created", "NOTE", "1");

        assertTrue(result);
        verify(userXpRepository, times(2)).save(any());
    }

    @Test
    void awardXpAccumulatesAndAdvancesLevelWhenThresholdCrossed() {
        UserXpEntity existing = new UserXpEntity();
        existing.setUserId(USER_ID);
        existing.setTotalXp(195L);
        existing.setCurrentLevel(1);
        when(xpHistoryRepository.existsByUserIdAndSourceTypeAndSourceIdAndReason(any(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(userXpRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(userXpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = xpService.awardXp(USER_ID, 10, "todo_completed", "TODO", "3");

        assertTrue(result);
        assertEquals(205L, existing.getTotalXp());
        assertEquals(2, existing.getCurrentLevel(), "205 XP crosses the 200 threshold into Budget Beginner");
    }

    @Test
    void awardXpDoesNotDoubleAwardOnConcurrentRaceCaughtByDbConstraint() {
        when(xpHistoryRepository.existsByUserIdAndSourceTypeAndSourceIdAndReason(any(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(xpHistoryRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        boolean result = xpService.awardXp(USER_ID, 5, "receipt_scanned", "RECEIPT", "9");

        assertFalse(result);
        verify(userXpRepository, never()).save(any());
    }

    @Test
    void levelForReturnsCorrectTierAtBoundaries() {
        assertEquals(1, XPService.levelFor(0).number());
        assertEquals(1, XPService.levelFor(199).number());
        assertEquals(2, XPService.levelFor(200).number());
        assertEquals(2, XPService.levelFor(599).number());
        assertEquals(3, XPService.levelFor(600).number());
        assertEquals(10, XPService.levelFor(15000).number());
        assertEquals(10, XPService.levelFor(999_999).number(), "max level caps rather than throwing");
    }

    @Test
    void nextLevelReturnsNullAtMaxLevelAndTheFollowingTierOtherwise() {
        assertEquals(2, XPService.nextLevel(0).number());
        assertNull(XPService.nextLevel(15000), "Financial Master (10) is the last level");
    }
}
