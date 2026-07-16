package org.example.finzin.repository;

import org.example.finzin.entity.GoldAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GoldAssetRepository extends JpaRepository<GoldAssetEntity, Long> {
    List<GoldAssetEntity> findByUserId(Long userId);

    @Query("SELECT SUM(a.currentValue) FROM GoldAssetEntity a WHERE a.userId = :userId AND a.currentValue IS NOT NULL")
    Double sumCurrentValueByUserId(@Param("userId") Long userId);

    @Query("SELECT SUM(a.weight * CASE a.weightUnit " +
           "WHEN 'GRAM' THEN 1.0 " +
           "WHEN 'VORI' THEN 11.664 " +
           "WHEN 'ANA' THEN 0.729 " +
           "WHEN 'RATI' THEN 0.12150 " +
           "WHEN 'POINT' THEN 0.02430 " +
           "ELSE 1.0 END) FROM GoldAssetEntity a WHERE a.userId = :userId")
    Double sumWeightInGramsByUserId(@Param("userId") Long userId);
}
