package org.example.finzin.gamification;

import org.example.finzin.entity.GamificationSettingsEntity;
import org.example.finzin.repository.GamificationSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GamificationSettingsServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private GamificationSettingsRepository repository;

    private GamificationSettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new GamificationSettingsService(repository);
    }

    private GamificationSettingsEntity settings(boolean enabled) {
        GamificationSettingsEntity e = new GamificationSettingsEntity();
        e.setUserId(USER_ID);
        e.setEnabled(enabled);
        e.setEnableNotifications(true);
        e.setShowDashboardWidget(true);
        e.setEnableCelebrations(true);
        e.setEnableChallenges(true);
        e.setEnableStreakTracking(true);
        e.setShowXp(true);
        return e;
    }

    @Test
    void getOrDefaultReturnsTheExistingRowWhenPresent() {
        GamificationSettingsEntity existing = settings(true);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        assertEquals(existing, settingsService.getOrDefault(USER_ID));
    }

    @Test
    void getOrDefaultCreatesAndSavesANewRowForAFirstTimeUser() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GamificationSettingsEntity result = settingsService.getOrDefault(USER_ID);

        assertEquals(USER_ID, result.getUserId());
        verify(repository).save(any());
    }

    @Test
    void isEnabledReturnsTrueWhenTheFlagIsSet() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(settings(true)));
        assertTrue(settingsService.isEnabled(USER_ID));
    }

    @Test
    void isEnabledReturnsFalseWhenTheFlagIsExplicitlyDisabled() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(settings(false)));
        assertFalse(settingsService.isEnabled(USER_ID));
    }

    @Test
    void isEnabledIsFalseRatherThanNullPointerWhenTheFlagIsNull() {
        GamificationSettingsEntity nullFlag = settings(true);
        nullFlag.setEnabled(null);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(nullFlag));

        assertFalse(settingsService.isEnabled(USER_ID));
    }

    @Test
    void updateOnlyOverwritesFieldsThatAreExplicitlyProvided() {
        GamificationSettingsEntity existing = settings(true);
        existing.setShowXp(true);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GamificationSettingsEntity result = settingsService.update(USER_ID, false, null, null, null, null, null, null);

        assertFalse(result.getEnabled(), "the explicitly-passed field must be updated");
        assertTrue(result.getShowXp(), "fields left null in the request must be left untouched, not reset");
    }

    @Test
    void updateSavesEveryFieldWhenAllAreProvided() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(settings(true)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        settingsService.update(USER_ID, false, false, false, false, false, false, false);

        ArgumentCaptor<GamificationSettingsEntity> captor = ArgumentCaptor.forClass(GamificationSettingsEntity.class);
        verify(repository).save(captor.capture());
        GamificationSettingsEntity saved = captor.getValue();
        assertFalse(saved.getEnabled());
        assertFalse(saved.getEnableNotifications());
        assertFalse(saved.getShowDashboardWidget());
        assertFalse(saved.getEnableCelebrations());
        assertFalse(saved.getEnableChallenges());
        assertFalse(saved.getEnableStreakTracking());
        assertFalse(saved.getShowXp());
    }
}
