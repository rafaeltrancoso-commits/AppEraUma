package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoryTextNormalizerTest {
    @Test
    void convertsEscapedNewlinesWithoutChangingOtherCharacters() {
        String text = "Olá.\\n\\n— Tudo bem?\\\\nSim, com acentos e / barras.";

        assertThat(StoryTextNormalizer.normalizeStoryText(text))
                .isEqualTo("Olá.\n\n— Tudo bem?\nSim, com acentos e / barras.");
    }
}
