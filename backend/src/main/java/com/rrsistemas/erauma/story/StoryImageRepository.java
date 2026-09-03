package com.rrsistemas.erauma.story;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoryImageRepository extends JpaRepository<StoryImage, UUID> {
    @EntityGraph(attributePaths = "story")
    Optional<StoryImage> findByIdAndStory_ActiveTrue(UUID id);
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
}
