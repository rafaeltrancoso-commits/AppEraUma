package com.rrsistemas.erauma.story;

public class StoryNarrativeValidationException extends AiGenerationException {
    private final String reason;

    public StoryNarrativeValidationException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
