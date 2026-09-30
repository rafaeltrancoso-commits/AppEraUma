package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StoryAudioTextTest {
    @Test
    void splitsAtNaturalBoundariesWithoutChangingOrderOrCuttingWords() {
        String text = "Uma frase curta. Outra frase um pouco maior para continuar a história. E o final chegou tranquilo.";

        List<String> chunks = StoryAudioText.chunks(text, 50);

        assertThat(chunks).hasSizeGreaterThan(1).allMatch(chunk -> chunk.length() <= 50);
        assertThat(String.join(" ", chunks)).isEqualTo(text);
    }

    @Test
    void hashIsStableForTheApprovedText() {
        assertThat(StoryAudioText.hash("texto aprovado")).isEqualTo(StoryAudioText.hash("texto aprovado"));
        assertThat(StoryAudioText.hash("texto aprovado")).isNotEqualTo(StoryAudioText.hash("texto alterado"));
    }
}
