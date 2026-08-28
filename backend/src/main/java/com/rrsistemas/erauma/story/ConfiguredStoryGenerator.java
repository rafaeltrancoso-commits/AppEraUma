package com.rrsistemas.erauma.story;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class ConfiguredStoryGenerator implements StoryGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredStoryGenerator.class);
    private final StoryAiProperties properties;
    private final OpenAIStoryGenerator openAIStoryGenerator;
    private final MockStoryGenerator mockStoryGenerator;

    public ConfiguredStoryGenerator(StoryAiProperties properties, OpenAIStoryGenerator openAIStoryGenerator, MockStoryGenerator mockStoryGenerator) {
        this.properties = properties;
        this.openAIStoryGenerator = openAIStoryGenerator;
        this.mockStoryGenerator = mockStoryGenerator;
    }

    @Override
    public GeneratedStory generate(StoryGenerationRequest request) {
        if (!properties.openAiEnabled()) {
            return mockStoryGenerator.generate(request);
        }

        try {
            return openAIStoryGenerator.generate(request);
        } catch (AiConfigurationException exception) {
            throw exception;
        } catch (AiUnavailableException | AiGenerationException exception) {
            if (!properties.aiFallbackEnabled()) {
                throw exception;
            }
            LOGGER.warn("OpenAI story generation failed; using mock fallback. reason={}", exception.getClass().getSimpleName());
            GeneratedStory fallback = mockStoryGenerator.generate(request);
            return new GeneratedStory(fallback.title(), fallback.summary(), fallback.narrativeArc(), fallback.chapters(), GenerationType.MOCK, "mock-fallback", fallback.model(), null, null, fallback.durationMs());
        }
    }
}
