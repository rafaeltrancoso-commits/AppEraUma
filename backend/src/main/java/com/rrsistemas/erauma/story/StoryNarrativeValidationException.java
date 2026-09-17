package com.rrsistemas.erauma.story;

import java.util.List;

public class StoryNarrativeValidationException extends AiGenerationException {
    private final String reason;
    private final List<String> missingCharacters;

    public StoryNarrativeValidationException(String reason, String message) {
        this(reason, message, List.of());
    }

    public StoryNarrativeValidationException(String reason, String message, List<String> missingCharacters) {
        super(message);
        this.reason = reason;
        this.missingCharacters = missingCharacters == null ? List.of() : List.copyOf(missingCharacters);
    }

    public String reason() {
        return reason;
    }

    public List<String> missingCharacters() {
        return missingCharacters;
    }
}
