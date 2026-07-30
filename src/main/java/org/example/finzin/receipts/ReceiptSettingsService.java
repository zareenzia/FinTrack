package org.example.finzin.receipts;

import org.example.finzin.entity.ReceiptSettingsEntity;
import org.example.finzin.repository.ReceiptSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class ReceiptSettingsService {
    private final ReceiptSettingsRepository repository;

    public ReceiptSettingsService(ReceiptSettingsRepository repository) {
        this.repository = repository;
    }

    public ReceiptSettingsEntity getOrDefault(Long userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            ReceiptSettingsEntity entity = new ReceiptSettingsEntity();
            entity.setUserId(userId);
            return repository.save(entity);
        });
    }

    public ReceiptSettingsEntity update(Long userId, Boolean enabled) {
        ReceiptSettingsEntity entity = getOrDefault(userId);
        if (enabled != null) entity.setEnabled(enabled);
        return repository.save(entity);
    }

    public boolean isEnabled(Long userId) {
        return Boolean.TRUE.equals(getOrDefault(userId).getEnabled());
    }
}
