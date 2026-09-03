package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockStoryGeneratorTest {
    private final MockStoryGenerator generator = new MockStoryGenerator();

    @Test
    void mockStoriesHaveBeginningMiddleAndEndingForEveryLength() {
        for (StoryLength length : StoryLength.values()) {
            GeneratedStory story = generator.generate(request(length));

            assertThat(story.narrativeArc().setup()).isNotBlank();
            assertThat(story.narrativeArc().centralSituation()).isNotBlank();
            assertThat(story.narrativeArc().protagonistAction()).isNotBlank();
            assertThat(story.narrativeArc().resolution()).isNotBlank();
            assertThat(story.narrativeArc().closingScene()).isNotBlank();
            assertThat(story.chapters()).hasSize(expectedChapters(length));
            assertThat(story.chapters().get(story.chapters().size() - 1).content())
                    .containsAnyOf("terminou", "voltou para casa", "tudo no lugar");
        }
    }

    private int expectedChapters(StoryLength length) {
        return StoryLengthSpec.of(length).expectedChapters();
    }

    private StoryGenerationRequest request(StoryLength length) {
        return new StoryGenerationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Nando Teste",
                LocalDate.now().minusYears(4),
                "Nando",
                "Luna",
                null,
                null,
                null,
                null,
                "Medo do escuro",
                "Jardim",
                "Dinossauro",
                StoryStyle.BEDTIME,
                length);
    }
}
