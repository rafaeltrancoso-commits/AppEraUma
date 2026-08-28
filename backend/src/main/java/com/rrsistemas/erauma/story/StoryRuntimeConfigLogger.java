package com.rrsistemas.erauma.story;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StoryRuntimeConfigLogger implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryRuntimeConfigLogger.class);
    private final StoryAiProperties storyAiProperties;
    private final OpenAiProperties openAiProperties;
    private final StoryImageProperties storyImageProperties;
    private final OpenAiImageProperties openAiImageProperties;
    private final Environment environment;

    public StoryRuntimeConfigLogger(
            StoryAiProperties storyAiProperties,
            OpenAiProperties openAiProperties,
            StoryImageProperties storyImageProperties,
            OpenAiImageProperties openAiImageProperties,
            Environment environment) {
        this.storyAiProperties = storyAiProperties;
        this.openAiProperties = openAiProperties;
        this.storyImageProperties = storyImageProperties;
        this.openAiImageProperties = openAiImageProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase)) {
            return;
        }
        LOGGER.info(
                "story_runtime_config story_generator_provider={} story_model={} story_image_enabled={} story_image_model={} openai_key_configured={}",
                storyAiProperties.openAiEnabled() ? "openai" : "mock",
                safe(openAiProperties.model()),
                storyImageProperties.generationEnabled(),
                safe(openAiImageProperties.model()),
                openAiProperties.apiKey() != null && !openAiProperties.apiKey().isBlank());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "not-configured" : value;
    }
}
