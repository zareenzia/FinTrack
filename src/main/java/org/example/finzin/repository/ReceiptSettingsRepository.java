package org.example.finzin.repository;

import org.example.finzin.entity.ReceiptSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptSettingsRepository extends JpaRepository<ReceiptSettingsEntity, Long> {
    Optional<ReceiptSettingsEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
