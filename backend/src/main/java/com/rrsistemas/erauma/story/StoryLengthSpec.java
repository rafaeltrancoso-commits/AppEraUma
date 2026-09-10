package com.rrsistemas.erauma.story;

public record StoryLengthSpec(StoryLength length, int maxOutputTokens, int retryMaxOutputTokens, int expectedChapters, int sceneImages, int minWords, int maxWords) {
    public static StoryLengthSpec of(StoryLength length) {
        return switch (length) {
            case SHORT -> new StoryLengthSpec(length, 2600, 3400, 2, 1, 350, 550);
            case MEDIUM -> new StoryLengthSpec(length, 5200, 6500, 4, 2, 700, 1000);
            case LONG -> new StoryLengthSpec(length, 9000, 11000, 6, 3, 1300, 1800);
        };
    }
}
