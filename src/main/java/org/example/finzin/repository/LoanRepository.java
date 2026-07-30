package org.example.finzin.repository;

import org.example.finzin.entity.LoanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoanRepository extends JpaRepository<LoanEntity, Long> {
    List<LoanEntity> findByUserId(Long userId);
    List<LoanEntity> findByUserIdAndStatus(Long userId, String status);
    void deleteByUserId(Long userId);
}
