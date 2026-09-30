package com.rrsistemas.erauma.story;

import java.util.UUID;

public record StoryAudioPartResponse(
        UUID id,
        UUID chapterId,
        int chapterNumber,
        int chunkIndex,
        StoryAudioStatus status,
        String contentUrl,
        String errorMessage
) {
    static StoryAudioPartResponse from(StoryAudio audio) {
        return new StoryAudioPartResponse(audio.getId(), audio.getChapter().getId(),
                audio.getChapter().getChapterNumber(), audio.getChunkIndex(), audio.getStatus(),
                audio.getStatus() == StoryAudioStatus.COMPLETED
                        ? "/api/story-audios/" + audio.getId() + "/content" : null,
                audio.getErrorMessage());
    }
}
