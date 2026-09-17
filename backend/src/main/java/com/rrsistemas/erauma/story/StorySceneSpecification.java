package com.rrsistemas.erauma.story;

import java.util.List;

public record StorySceneSpecification(
        String setting,
        String timeOfDay,
        String characters,
        int maximumCharacterCount,
        String mainAction,
        String centralObject,
        String mood,
        List<String> mustInclude,
        List<String> mustNotInclude,
        boolean knownReferenceAdapted) {

    public StorySceneSpecification {
        if (blank(setting) || blank(characters) || blank(mainAction) || mustInclude == null || mustInclude.isEmpty()) {
            throw new IllegalArgumentException("Especificação visual incompleta ou genérica demais.");
        }
        mustInclude = List.copyOf(mustInclude);
        mustNotInclude = mustNotInclude == null ? List.of() : List.copyOf(mustNotInclude);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
