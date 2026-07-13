package org.example.finzin.repository;

import org.example.finzin.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByUserId(Long userId);
    List<AccountEntity> findByUserIdAndStatus(Long userId, String status);
    boolean existsByUserIdAndAccountNicknameIgnoreCase(Long userId, String nickname);
}
