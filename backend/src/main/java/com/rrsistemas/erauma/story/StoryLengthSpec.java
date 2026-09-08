package com.rrsistemas.erauma.story;

public record StoryLengthSpec(StoryLength length, int maxOutputTokens, int retryMaxOutputTokens, int expectedChapters, int sceneImages) {
    public static StoryLengthSpec of(StoryLength length) {
        return switch (length) {
            case SHORT -> new StoryLengthSpec(length, 2200, 3200, 2, 1);
            case MEDIUM -> new StoryLengthSpec(length, 4200, 5600, 4, 2);
            case LONG -> new StoryLengthSpec(length, 7000, 9000, 6, 3);
        };
    }
}
