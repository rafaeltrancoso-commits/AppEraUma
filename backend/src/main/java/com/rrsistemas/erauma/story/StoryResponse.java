package com.rrsistemas.erauma.story;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryResponse(
        UUID id,
        UUID familyId,
        String title,
        String summary,
        String content,
        String theme,
        String place,
        String mainCharacterName,
        String secondCharacterName,
        String favoriteAnimal,
        StoryStyle style,
        StoryLength length,
        boolean favorite,
        GenerationType generationType,
        StoryChildResponse child,
        StoryMomentResponse sourceMoment,
        List<StoryChapterResponse> chapters,
        List<StoryImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    static StoryResponse from(Story story) {
        return new StoryResponse(
                story.getId(), story.getFamilyId(), story.getTitle(), StoryTextNormalizer.normalizeStoryText(story.getSummary()), StoryTextNormalizer.normalizeStoryText(story.getContent()), story.getTheme(),
                story.getPlace(), story.getMainCharacterName(), story.getSecondCharacterName(), story.getFavoriteAnimal(), story.getStyle(), story.getLength(), story.isFavorite(),
                story.getGenerationType(), StoryChildResponse.from(story.getChild()), StoryMomentResponse.from(story.getSourceMoment()),
                story.getChapters().stream().map(StoryChapterResponse::from).toList(),
                story.getImages().stream().map(StoryImageResponse::from).toList(), story.getCreatedAt(), story.getUpdatedAt());
    }
}
