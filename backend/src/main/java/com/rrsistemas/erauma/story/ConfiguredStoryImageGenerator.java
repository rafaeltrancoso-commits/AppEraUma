package com.rrsistemas.erauma.story;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class ConfiguredStoryImageGenerator implements StoryImageGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredStoryImageGenerator.class);
    private final StoryAiProperties storyAiProperties;
    private final OpenAIStoryImageGenerator openAIStoryImageGenerator;
    private final MockStoryImageGenerator mockStoryImageGenerator;

    public ConfiguredStoryImageGenerator(StoryAiProperties storyAiProperties, OpenAIStoryImageGenerator openAIStoryImageGenerator, MockStoryImageGenerator mockStoryImageGenerator) {
        this.storyAiProperties = storyAiProperties;
        this.openAIStoryImageGenerator = openAIStoryImageGenerator;
        this.mockStoryImageGenerator = mockStoryImageGenerator;
    }

    @Override
    public GeneratedStoryImage generate(String prompt) {
        if (!storyAiProperties.openAiEnabled()) {
            return mockStoryImageGenerator.generate(prompt);
        }
        try {
            return openAIStoryImageGenerator.generate(prompt);
        } catch (AiGenerationException | AiUnavailableException exception) {
            LOGGER.warn("openai_story_image_generation_failed reason={}", exception.getClass().getSimpleName());
            throw exception;
        }
    }
}
