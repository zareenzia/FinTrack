package org.example.finzin.receipts;

import org.example.finzin.entity.ReceiptSettingsEntity;
import org.example.finzin.repository.ReceiptSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (matching BudgetPlanServiceTest's convention) for ReceiptSettingsService:
 * lazy per-user default creation, updating the enabled flag, and the isEnabled() convenience read.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptSettingsServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private ReceiptSettingsRepository repository;

    private ReceiptSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptSettingsService(repository);
    }

    // ================================================================================
    // getOrDefault
    // ================================================================================

    @Test
    void getOrDefaultReturnsExistingSettingsWithoutCreatingANewOne() {
        ReceiptSettingsEntity existing = new ReceiptSettingsEntity();
        existing.setUserId(USER_ID);
        existing.setEnabled(false);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        ReceiptSettingsEntity result = service.getOrDefault(USER_ID);

        assertSame(existing, result);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getOrDefaultCreatesAndPersistsANewRowForTheUserWhenNoneExists() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptSettingsEntity result = service.getOrDefault(USER_ID);

        assertEquals(USER_ID, result.getUserId());
        ArgumentCaptor<ReceiptSettingsEntity> captor = ArgumentCaptor.forClass(ReceiptSettingsEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    // ================================================================================
    // update
    // ================================================================================

    @Test
    void updateSetsEnabledFlagWhenProvided() {
        ReceiptSettingsEntity existing = new ReceiptSettingsEntity();
        existing.setUserId(USER_ID);
        existing.setEnabled(true);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        ReceiptSettingsEntity result = service.update(USER_ID, false);

        assertFalse(result.getEnabled());
        verify(repository).save(existing);
    }

    @Test
    void updateLeavesEnabledFlagUnchangedWhenArgumentIsNull() {
        ReceiptSettingsEntity existing = new ReceiptSettingsEntity();
        existing.setUserId(USER_ID);
        existing.setEnabled(true);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        ReceiptSettingsEntity result = service.update(USER_ID, null);

        assertTrue(result.getEnabled(), "a null argument must leave the existing enabled flag untouched");
    }

    @Test
    void updateCreatesDefaultSettingsFirstWhenNoneExistYet() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptSettingsEntity result = service.update(USER_ID, false);

        assertEquals(USER_ID, result.getUserId());
        assertFalse(result.getEnabled());
        verify(repository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    // ================================================================================
    // isEnabled
    // ================================================================================

    @Test
    void isEnabledReturnsTrueWhenSettingsEnabledFlagIsTrue() {
        ReceiptSettingsEntity existing = new ReceiptSettingsEntity();
        existing.setUserId(USER_ID);
        existing.setEnabled(true);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        assertTrue(service.isEnabled(USER_ID));
    }

    @Test
    void isEnabledReturnsFalseWhenSettingsEnabledFlagIsFalse() {
        ReceiptSettingsEntity existing = new ReceiptSettingsEntity();
        existing.setUserId(USER_ID);
        existing.setEnabled(false);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        assertFalse(service.isEnabled(USER_ID));
    }

    @Test
    void isEnabledReturnsFalseWhenEnabledFlagIsNull() {
        ReceiptSettingsEntity existing = new ReceiptSettingsEntity();
        existing.setUserId(USER_ID);
        existing.setEnabled(null);
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        assertFalse(service.isEnabled(USER_ID));
    }
}
