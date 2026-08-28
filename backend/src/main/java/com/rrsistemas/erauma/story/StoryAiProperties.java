package com.rrsistemas.erauma.story;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.story")
public record StoryAiProperties(
        String generator,
        boolean aiFallbackEnabled,
        int dailyLimit,
        int illustratedDailyLimit
) {
    public boolean openAiEnabled() {
        return "openai".equalsIgnoreCase(generator);
    }
}
