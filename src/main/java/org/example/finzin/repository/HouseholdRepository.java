package org.example.finzin.repository;

import org.example.finzin.entity.HouseholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HouseholdRepository extends JpaRepository<HouseholdEntity, Long> {
}
