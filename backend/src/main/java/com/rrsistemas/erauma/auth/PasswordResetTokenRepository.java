package com.rrsistemas.erauma.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query(value = "update password_reset_token set used_at = current_timestamp where user_id = :userId and used_at is null", nativeQuery = true)
    int invalidateActiveTokens(@Param("userId") UUID userId);
}
