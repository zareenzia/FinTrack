package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.VoiceSettingsEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VoiceSettingsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private VoiceSettingsRepository repository;

    private VoiceSettingsEntity settings(Long userId, String language) {
        VoiceSettingsEntity s = new VoiceSettingsEntity();
        s.setUserId(userId);
        s.setEnabled(true);
        s.setLanguage(language);
        s.setSpeechProvider("browser");
        s.setAutoStopSilenceSeconds(3);
        s.setNoiseReduction(false);
        s.setSaveAudioRecordings(false);
        s.setMaxRecordingLengthSeconds(60);
        s.setSpeechSpeed(1.0);
        return s;
    }

    @Test
    void findByUserIdReturnsSettingsForThatUser() {
        entityManager.persistAndFlush(settings(1L, "en-US"));
        entityManager.clear();

        Optional<VoiceSettingsEntity> found = repository.findByUserId(1L);

        assertTrue(found.isPresent());
        assertEquals("en-US", found.get().getLanguage());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNoSettingsForUser() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersSettings() {
        VoiceSettingsEntity s1 = entityManager.persistAndFlush(settings(1L, "en-US"));
        VoiceSettingsEntity s2 = entityManager.persistAndFlush(settings(2L, "en-US"));
        entityManager.clear();

        repository.deleteByUserId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(s1.getId()).isEmpty());
        assertTrue(repository.findById(s2.getId()).isPresent());
    }
}
