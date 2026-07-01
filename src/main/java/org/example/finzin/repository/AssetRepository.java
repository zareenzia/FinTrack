package org.example.finzin.repository;

import org.example.finzin.entity.AssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, Long> {
    @Query("SELECT SUM(a.value) FROM AssetEntity a")
    Double sumAllValues();
}
