package com.rrsistemas.erauma.story;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.story.audio")
public record StoryAudioProperties(boolean enabled, int maxAttempts, int chunkMaxCharacters) {
    public int effectiveMaxAttempts() { return maxAttempts > 0 ? maxAttempts : 3; }
    public int effectiveChunkMaxCharacters() {
        return Math.min(Math.max(chunkMaxCharacters, 500), 4096);
    }
}
