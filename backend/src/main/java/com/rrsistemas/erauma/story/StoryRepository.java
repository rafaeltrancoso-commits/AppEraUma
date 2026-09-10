package com.rrsistemas.erauma.story;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StoryRepository extends JpaRepository<Story, UUID> {
    Optional<Story> findByIdAndActiveTrue(UUID id);

    @EntityGraph(attributePaths = {"child", "sourceMoment", "chapters", "images", "characters", "characters.character"})
    Optional<Story> findWithChildAndSourceMomentAndChaptersAndImagesByIdAndActiveTrue(UUID id);

    Optional<Story> findByCreatedBy_IdAndIdempotencyKeyAndActiveTrue(UUID userId, String idempotencyKey);
    java.util.List<Story> findByGenerationStatusInAndActiveTrue(java.util.List<StoryGenerationStatus> statuses);
    java.util.List<Story> findByGenerationStatusAndUpdatedAtBeforeAndActiveTrue(StoryGenerationStatus status, java.time.Instant updatedAt);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select story from Story story where story.id = :id and story.active = true")
    Optional<Story> findForGeneration(@Param("id") UUID id);
    @Modifying
    @Query("update Story story set story.updatedAt = :now where story.id = :id and story.active = true and story.generationStatus = 'PROCESSANDO_TEXTO'")
    int heartbeatGeneration(@Param("id") UUID id, @Param("now") java.time.Instant now);
    long countByCreatedBy_IdAndCreatedAtGreaterThanEqual(UUID userId, java.time.Instant createdAt);

    @Query("""
            select s from Story s
            where s.family.id = :familyId
              and s.active = true
              and (:childId is null or s.child.id = :childId)
              and (:favorite is null or s.favorite = :favorite)
              and (:style is null or s.style = :style)
              and (:generationMode is null or (:generationMode = 'TEXT_ONLY' and s.images is empty) or (:generationMode = 'ILLUSTRATED' and s.images is not empty))
              and (cast(:from as timestamp) is null or s.createdAt >= :from)
              and (cast(:to as timestamp) is null or s.createdAt < :to)
            """)
    Page<Story> search(
            @Param("familyId") UUID familyId,
            @Param("childId") UUID childId,
            @Param("favorite") Boolean favorite,
            @Param("style") StoryStyle style,
            @Param("generationMode") String generationMode,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            Pageable pageable);

    java.util.List<Story> findBySourceMoment_IdAndActiveTrueOrderByCreatedAtDesc(UUID momentId);

    @Query("""
            select count(s) from Story s
            where s.family.id = :familyId
              and s.active = true
              and s.images is not empty
              and s.createdAt >= :from
              and s.createdAt < :to
            """)
    long countIllustratedByFamilyAndCreatedAtBetween(
            @Param("familyId") UUID familyId,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);
}
