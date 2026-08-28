package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.story.StoryImageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MomentStoryResponse(
        UUID id,
        String title,
        String summary,
        String mainCharacterName,
        String secondCharacterName,
        String theme,
        Instant createdAt,
        List<StoryImageResponse> images
) {}
