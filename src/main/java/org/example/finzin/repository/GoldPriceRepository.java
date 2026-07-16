package org.example.finzin.repository;

import org.example.finzin.entity.GoldPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoldPriceRepository extends JpaRepository<GoldPriceEntity, Long> {

    @Query("SELECT p FROM GoldPriceEntity p WHERE p.purity = :purity AND p.unit = :unit ORDER BY p.retrievedAt DESC LIMIT 1")
    Optional<GoldPriceEntity> findLatestByPurityAndUnit(@Param("purity") String purity, @Param("unit") String unit);

    @Query("SELECT p FROM GoldPriceEntity p WHERE p.retrievedAt = (SELECT MAX(p2.retrievedAt) FROM GoldPriceEntity p2)")
    List<GoldPriceEntity> findLatestBatch();

    @Query("SELECT MAX(p.retrievedAt) FROM GoldPriceEntity p")
    Optional<LocalDateTime> findLatestRetrievedAt();
}
