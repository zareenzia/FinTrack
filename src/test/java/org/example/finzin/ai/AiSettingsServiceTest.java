package org.example.finzin.ai;

import org.example.finzin.entity.AiSettingsEntity;
import org.example.finzin.repository.AiSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (no Spring context). Locks in the server-side validation bounds
 * documented on {@link AiSettingsService#update} ("Returns null if maxTokens/temperature are out
 * of range — caller responds 400") — the client-side form validation is not a substitute for this,
 * since the API is reachable directly.
 */
@ExtendWith(MockitoExtension.class)
class AiSettingsServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private AiSettingsRepository repository;

    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        service = new AiSettingsService(repository);
    }

    private AiSettingsEntity existing() {
        AiSettingsEntity e = new AiSettingsEntity();
        e.setUserId(USER_ID);
        e.setModel("gpt-5");
        e.setMaxTokens(800);
        e.setTemperature(0.3);
        e.setEnabled(true);
        return e;
    }

    @Test
    void getOrDefaultCreatesAndSavesDefaultSettingsWhenNoneExist() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(AiSettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsEntity result = service.getOrDefault(USER_ID);

        assertEquals(USER_ID, result.getUserId());
        verify(repository).save(any(AiSettingsEntity.class));
    }

    @Test
    void getOrDefaultReturnsExistingSettingsWithoutSavingAgain() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing()));

        service.getOrDefault(USER_ID);

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsMaxTokensBelowOneHundred() {
        AiSettingsEntity result = service.update(USER_ID, null, 99, null, null, null, null, null, null, null, null);

        assertNull(result, "99 is below the documented 100-4000 floor");
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsMaxTokensAboveFourThousand() {
        AiSettingsEntity result = service.update(USER_ID, null, 4001, null, null, null, null, null, null, null, null);

        assertNull(result, "4001 is above the documented 100-4000 ceiling");
    }

    @Test
    void updateAcceptsMaxTokensAtTheInclusiveBoundaries() {
        // Must return a fresh entity per call: update() mutates and returns it in place, so reusing
        // the same instance across both calls below would make the first assertion observe the
        // second call's mutation instead of its own.
        when(repository.findByUserId(USER_ID)).thenAnswer(inv -> Optional.of(existing()));
        when(repository.save(any(AiSettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsEntity low = service.update(USER_ID, null, 100, null, null, null, null, null, null, null, null);
        AiSettingsEntity high = service.update(USER_ID, null, 4000, null, null, null, null, null, null, null, null);

        assertEquals(100, low.getMaxTokens());
        assertEquals(4000, high.getMaxTokens());
    }

    @Test
    void updateRejectsNegativeTemperature() {
        AiSettingsEntity result = service.update(USER_ID, null, null, -0.1, null, null, null, null, null, null, null);

        assertNull(result, "temperature must be 0-2");
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsTemperatureAboveTwo() {
        AiSettingsEntity result = service.update(USER_ID, null, null, 2.1, null, null, null, null, null, null, null);

        assertNull(result);
    }

    @Test
    void updateAcceptsTemperatureAtTheInclusiveBoundaries() {
        // Must return a fresh entity per call: update() mutates and returns it in place, so reusing
        // the same instance across both calls below would make the first assertion observe the
        // second call's mutation instead of its own.
        when(repository.findByUserId(USER_ID)).thenAnswer(inv -> Optional.of(existing()));
        when(repository.save(any(AiSettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsEntity zero = service.update(USER_ID, null, null, 0.0, null, null, null, null, null, null, null);
        AiSettingsEntity two = service.update(USER_ID, null, null, 2.0, null, null, null, null, null, null, null);

        assertEquals(0.0, zero.getTemperature());
        assertEquals(2.0, two.getTemperature());
    }

    @Test
    void updateLeavesUntouchedFieldsAsIsWhenNullIsPassed() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing()));
        when(repository.save(any(AiSettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsEntity result = service.update(USER_ID, null, null, null, false, null, null, null, null, null, null);

        assertEquals("gpt-5", result.getModel(), "untouched fields must keep their prior value");
        assertEquals(false, result.getEnabled());
    }

    @Test
    void updateTrimsAndAppliesAnExplicitModelName() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing()));
        when(repository.save(any(AiSettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsEntity result = service.update(USER_ID, "  gpt-5-mini  ", null, null, null, null, null, null, null, null, null);

        assertEquals("gpt-5-mini", result.getModel());
    }

    @Test
    void updateIgnoresABlankModelName() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing()));
        when(repository.save(any(AiSettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsEntity result = service.update(USER_ID, "   ", null, null, null, null, null, null, null, null, null);

        assertEquals("gpt-5", result.getModel());
    }
}
