package org.example.finzin.repository;

import org.example.finzin.entity.SidebarPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SidebarPreferenceRepository extends JpaRepository<SidebarPreferenceEntity, Long> {
    Optional<SidebarPreferenceEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
