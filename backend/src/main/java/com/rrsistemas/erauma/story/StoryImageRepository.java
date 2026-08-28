package com.rrsistemas.erauma.story;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryImageRepository extends JpaRepository<StoryImage, UUID> {
    @EntityGraph(attributePaths = "story")
    Optional<StoryImage> findByIdAndStory_ActiveTrue(UUID id);
    List<StoryImage> findByStory_IdOrderBySortOrderAsc(UUID storyId);
}
