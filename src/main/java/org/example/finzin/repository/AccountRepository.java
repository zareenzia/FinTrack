package org.example.finzin.repository;

import jakarta.persistence.LockModeType;
import org.example.finzin.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByUserId(Long userId);
    List<AccountEntity> findByUserIdAndStatus(Long userId, String status);
    boolean existsByUserIdAndAccountNicknameIgnoreCase(Long userId, String nickname);
    void deleteByUserId(Long userId);

    /** Row-level lock held until the enclosing @Transactional commits. Used whenever currentBalance
     *  is about to be read-modified-written, so two concurrent transactions touching the same
     *  account (e.g. several bulk-added transactions submitted in parallel) serialize on that row
     *  instead of both reading the same stale balance and one silently clobbering the other's write. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") Long id);
}
