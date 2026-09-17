package com.rrsistemas.erauma.family;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyRepository extends JpaRepository<Family, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Family f where f.id = :id")
    Optional<Family> findByIdForUpdate(@Param("id") UUID id);
}

