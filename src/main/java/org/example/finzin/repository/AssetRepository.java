package org.example.finzin.repository;

import org.example.finzin.entity.AssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, Long> {
    @Query("SELECT SUM(a.value) FROM AssetEntity a")
    Double sumAllValues();
    
    List<AssetEntity> findByUserId(Long userId);
    
    @Query("SELECT SUM(a.value) FROM AssetEntity a WHERE a.userId = :userId")
    Double sumValueByUserId(@Param("userId") Long userId);
    
    @Query("SELECT SUM(a.value) FROM AssetEntity a WHERE a.userId = :userId")
    Double sumValuesByUserId(@Param("userId") Long userId);
}

