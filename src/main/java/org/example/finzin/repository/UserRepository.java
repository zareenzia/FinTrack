package org.example.finzin.repository;

import org.example.finzin.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    
    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.username) = LOWER(?1) OR LOWER(u.email) = LOWER(?2)")
    Optional<UserEntity> findByUsernameOrEmail(String username, String email);
    
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE LOWER(u.username) = LOWER(?1)")
    boolean existsByUsernameIgnoreCase(String username);
    
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE LOWER(u.email) = LOWER(?1)")
    boolean existsByEmailIgnoreCase(String email);
}
