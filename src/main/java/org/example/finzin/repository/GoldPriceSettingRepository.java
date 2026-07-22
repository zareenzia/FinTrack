package org.example.finzin.repository;

import org.example.finzin.entity.GoldPriceSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GoldPriceSettingRepository extends JpaRepository<GoldPriceSettingEntity, Long> {
    Optional<GoldPriceSettingEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
