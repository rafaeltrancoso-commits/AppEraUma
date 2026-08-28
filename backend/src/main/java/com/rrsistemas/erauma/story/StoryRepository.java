package com.rrsistemas.erauma.story;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryRepository extends JpaRepository<Story, UUID> {
    Optional<Story> findByIdAndActiveTrue(UUID id);

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
