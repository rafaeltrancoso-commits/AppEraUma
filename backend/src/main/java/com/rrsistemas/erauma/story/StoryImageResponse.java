package com.rrsistemas.erauma.story;

import java.util.UUID;

public record StoryImageResponse(
        UUID id,
        StoryImageType type,
        UUID chapterId,
        StoryImageStatus status,
        String contentUrl,
        String model,
        String size,
        String quality,
        int sortOrder,
        Integer chapterStart,
        Integer chapterEnd
) {
    public static StoryImageResponse from(StoryImage image) {
        UUID chapterId = image.getChapter() == null ? null : image.getChapter().getId();
        String contentUrl = image.getStatus() == StoryImageStatus.GENERATED ? "/api/story-images/" + image.getId() + "/content" : null;
        return new StoryImageResponse(image.getId(), image.getImageType(), chapterId, image.getStatus(), contentUrl, image.getModel(), image.getSize(), image.getQuality(), image.getSortOrder(), image.getChapterStart(), image.getChapterEnd());
    }
}
