package com.rrsistemas.erauma.story;

import java.time.LocalDate;
import java.util.UUID;

public record StoryCharacterPrompt(
        UUID id,
        String name,
        String nickname,
        LocalDate birthDate,
        int selectionOrder,
        StoryCharacterRole role,
        String visualDescription
) {}
