package com.green.imagecore.repositories;

import com.green.imagecore.entities.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    Optional<PasswordReset> findByTokenHash(String tokenHash);
    long deleteByExpiresAtBefore(Instant cutoff);
    long deleteByUserId(Long userId);
}
