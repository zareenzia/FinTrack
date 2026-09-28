package org.example.finzin.repository;

import org.example.finzin.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, Long> {
    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
}
