package com.rrsistemas.erauma.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailAndActiveTrue(String email);
    Optional<AppUser> findByEmailAndActiveTrueAndDeletionRequestedAtIsNull(String email);
    boolean existsByIdAndActiveTrueAndDeletionRequestedAtIsNull(UUID id);
    boolean existsByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id and user.active = true and user.deletionRequestedAt is null")
    Optional<AppUser> findForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id and user.deletionRequestedAt is not null")
    Optional<AppUser> findDeletionRequestedForUpdate(@Param("id") UUID id);
    @Query("select user.id from AppUser user where user.deletionRequestedAt is not null order by user.deletionRequestedAt")
    List<UUID> findDeletionRequestedIds();
}
