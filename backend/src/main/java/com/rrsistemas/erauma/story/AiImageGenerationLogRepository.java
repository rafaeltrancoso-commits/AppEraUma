package com.rrsistemas.erauma.story;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiImageGenerationLogRepository extends JpaRepository<AiImageGenerationLog, UUID> {
    long countByUser_IdAndStatusAndCreatedAtGreaterThanEqual(UUID userId, StoryImageStatus status, Instant createdAt);
}
