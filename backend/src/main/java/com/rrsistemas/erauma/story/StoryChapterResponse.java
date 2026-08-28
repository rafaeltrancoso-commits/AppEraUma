package com.rrsistemas.erauma.story;

import java.util.UUID;

public record StoryChapterResponse(UUID id, int number, String title, String content) {
    static StoryChapterResponse from(StoryChapter chapter) {
        return new StoryChapterResponse(chapter.getId(), chapter.getChapterNumber(), chapter.getTitle(), StoryTextNormalizer.normalizeStoryText(chapter.getContent()));
    }
}
