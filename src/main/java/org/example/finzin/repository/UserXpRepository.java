package org.example.finzin.repository;

import org.example.finzin.entity.UserXpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserXpRepository extends JpaRepository<UserXpEntity, Long> {
    Optional<UserXpEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
