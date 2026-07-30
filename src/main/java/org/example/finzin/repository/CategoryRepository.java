package org.example.finzin.repository;

import org.example.finzin.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
    
    Optional<CategoryEntity> findByUserIdAndName(Long userId, String name);
    List<CategoryEntity> findByUserId(Long userId);
    
    @Query("SELECT c FROM CategoryEntity c WHERE c.userId = :userId AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<CategoryEntity> searchByUserId(@Param("userId") Long userId, @Param("search") String search);

    /** Returns categories whose categoryType matches the given type, OR is null/"general" (usable for any type). */
    @Query("SELECT c FROM CategoryEntity c WHERE c.userId = :userId AND (c.categoryType = :type OR c.categoryType = 'general' OR c.categoryType IS NULL)")
    List<CategoryEntity> findByUserIdAndCategoryTypeOrGeneral(@Param("userId") Long userId, @Param("type") String type);

    void deleteByUserId(Long userId);
}

