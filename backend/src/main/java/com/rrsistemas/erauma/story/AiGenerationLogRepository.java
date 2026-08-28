package com.rrsistemas.erauma.story;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, UUID> {
    long countByUser_IdAndCreatedAtGreaterThanEqual(UUID userId, Instant createdAt);
}
