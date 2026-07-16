package org.example.finzin.repository;

import org.example.finzin.entity.InvestmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InvestmentRepository extends JpaRepository<InvestmentEntity, Long> {
    List<InvestmentEntity> findByUserId(Long userId);
    List<InvestmentEntity> findByUserIdAndInvestmentType(Long userId, String investmentType);
}
