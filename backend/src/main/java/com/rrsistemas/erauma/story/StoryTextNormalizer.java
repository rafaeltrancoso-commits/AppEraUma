package com.rrsistemas.erauma.story;

final class StoryTextNormalizer {
    private StoryTextNormalizer() {}

    static String normalizeStoryText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value
                .replace("\\\\r\\\\n", "\n")
                .replace("\\\\n", "\n")
                .replace("\\\\r", "\n")
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");
    }
}
