package org.example.finzin.repository;

import org.example.finzin.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    boolean existsByNameIgnoreCase(String name);
}
