package com.rrsistemas.erauma.story;

public record StoryLengthSpec(StoryLength length, int maxOutputTokens, int expectedChapters, int sceneImages) {
    public static StoryLengthSpec of(StoryLength length) {
        return switch (length) {
            case SHORT -> new StoryLengthSpec(length, 1400, 2, 1);
            case MEDIUM -> new StoryLengthSpec(length, 2500, 4, 2);
            case LONG -> new StoryLengthSpec(length, 4000, 6, 3);
        };
    }
}
