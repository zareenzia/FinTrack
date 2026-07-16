package org.example.finzin.repository;

import org.example.finzin.entity.NetWorthSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshotEntity, Long> {
    Optional<NetWorthSnapshotEntity> findByUserIdAndSnapshotMonth(Long userId, String snapshotMonth);
    Optional<NetWorthSnapshotEntity> findTopByUserIdAndSnapshotMonthLessThanOrderBySnapshotMonthDesc(Long userId, String snapshotMonth);
}
