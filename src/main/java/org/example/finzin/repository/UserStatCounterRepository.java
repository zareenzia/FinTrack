package org.example.finzin.repository;

import org.example.finzin.entity.UserStatCounterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStatCounterRepository extends JpaRepository<UserStatCounterEntity, Long> {
    Optional<UserStatCounterEntity> findByUserIdAndCounterKey(Long userId, String counterKey);
    void deleteByUserId(Long userId);
}
