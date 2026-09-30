package com.rrsistemas.erauma.story;

import java.util.List;
import java.util.UUID;

public record StoryNarrationResponse(
        UUID storyId,
        StoryNarrationStatus status,
        String voice,
        List<StoryAudioPartResponse> parts
) {
    static StoryNarrationResponse from(UUID storyId, String voice, List<StoryAudio> audios) {
        List<StoryAudioPartResponse> parts = audios.stream().map(StoryAudioPartResponse::from).toList();
        StoryNarrationStatus status;
        if (audios.isEmpty()) status = StoryNarrationStatus.NOT_REQUESTED;
        else if (audios.stream().allMatch(item -> item.getStatus() == StoryAudioStatus.COMPLETED)) status = StoryNarrationStatus.COMPLETED;
        else if (audios.stream().anyMatch(item -> item.getStatus() == StoryAudioStatus.PROCESSING)) status = StoryNarrationStatus.PROCESSING;
        else if (audios.stream().anyMatch(item -> item.getStatus() == StoryAudioStatus.PENDING)) status = StoryNarrationStatus.PENDING;
        else status = StoryNarrationStatus.FAILED;
        return new StoryNarrationResponse(storyId, status, voice, parts);
    }
}
