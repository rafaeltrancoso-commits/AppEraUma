package com.rrsistemas.erauma.story;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface StoryImageRepository extends JpaRepository<StoryImage, UUID> {
    @EntityGraph(attributePaths = "story")
    Optional<StoryImage> findByIdAndStory_ActiveTrue(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from StoryImage image where image.id = :id and image.story.active = true")
    Optional<StoryImage> findForRetry(@Param("id") UUID id);
    List<StoryImage> findByStory_IdOrderBySortOrderAsc(UUID storyId);
    Optional<StoryImage> findByStory_IdAndImageTypeAndSortOrder(UUID storyId, StoryImageType imageType, int sortOrder);
    List<StoryImage> findByStory_IdAndStatusInOrderBySortOrderAsc(UUID storyId, List<StoryImageStatus> statuses);
    List<StoryImage> findByStory_IdAndStatusOrderBySortOrderAsc(UUID storyId, StoryImageStatus status);

    @Query("""
            select distinct image.story.id from StoryImage image
            where image.story.active = true
              and image.status in :statuses
            """)
    List<UUID> findDistinctStoryIdsByStatusIn(List<StoryImageStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select image from StoryImage image
            where image.story.id = :storyId
              and image.story.active = true
              and (image.status = 'PENDING'
                   or (:recoverStale = true and image.status = 'GENERATING' and image.updatedAt < :staleBefore))
            order by image.sortOrder
            """)
    List<StoryImage> findClaimable(
            @Param("storyId") UUID storyId,
            @Param("recoverStale") boolean recoverStale,
            @Param("staleBefore") java.time.Instant staleBefore);

    @Query("""
            select distinct image.story.id from StoryImage image
            where image.story.active = true
              and image.status = 'GENERATING'
              and image.updatedAt < :staleBefore
            """)
    List<UUID> findDistinctStaleGeneratingStoryIds(@Param("staleBefore") java.time.Instant staleBefore);

    @Modifying
    @Query("update StoryImage image set image.updatedAt = :now where image.id = :id and image.status = 'GENERATING'")
    int heartbeatGeneration(@Param("id") UUID id, @Param("now") java.time.Instant now);
}
