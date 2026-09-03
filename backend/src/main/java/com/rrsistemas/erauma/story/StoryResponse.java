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
        StoryIllustrationStatus illustrationStatus,
        Instant createdAt,
        Instant updatedAt
) {
    static StoryResponse from(Story story) {
        return new StoryResponse(
                story.getId(), story.getFamilyId(), story.getTitle(), StoryTextNormalizer.normalizeStoryText(story.getSummary()), StoryTextNormalizer.normalizeStoryText(story.getContent()), story.getTheme(),
                story.getPlace(), story.getMainCharacterName(), story.getSecondCharacterName(), story.getFavoriteAnimal(), story.getStyle(), story.getLength(), story.isFavorite(),
                story.getGenerationType(), StoryChildResponse.from(story.getChild()), StoryMomentResponse.from(story.getSourceMoment()),
                story.getChapters().stream().map(StoryChapterResponse::from).toList(),
                story.getImages().stream().map(StoryImageResponse::from).toList(),
                illustrationStatus(story), story.getCreatedAt(), story.getUpdatedAt());
    }

    private static StoryIllustrationStatus illustrationStatus(Story story) {
        List<StoryImage> images = story.getImages();
        if (images == null || images.isEmpty()) {
            return StoryIllustrationStatus.NOT_REQUESTED;
        }
        long generated = images.stream().filter(image -> image.getStatus() == StoryImageStatus.GENERATED).count();
        long failed = images.stream().filter(image -> image.getStatus() == StoryImageStatus.FAILED).count();
        long generating = images.stream().filter(image -> image.getStatus() == StoryImageStatus.GENERATING).count();
        long pending = images.stream().filter(image -> image.getStatus() == StoryImageStatus.PENDING).count();
        if (generated == images.size()) {
            return StoryIllustrationStatus.GENERATED;
        }
        if (failed == images.size()) {
            return StoryIllustrationStatus.FAILED;
        }
        if (generated > 0 && failed > 0) {
            return StoryIllustrationStatus.PARTIALLY_FAILED;
        }
        if (generated > 0) {
            return StoryIllustrationStatus.PARTIALLY_GENERATED;
        }
        if (failed > 0) {
            return StoryIllustrationStatus.PARTIALLY_FAILED;
        }
        if (generating > 0) {
            return StoryIllustrationStatus.GENERATING;
        }
        if (pending > 0) {
            return StoryIllustrationStatus.PENDING;
        }
        return StoryIllustrationStatus.NOT_REQUESTED;
    }
}
