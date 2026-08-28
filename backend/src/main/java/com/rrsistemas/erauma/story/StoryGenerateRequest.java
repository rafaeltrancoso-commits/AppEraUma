package com.rrsistemas.erauma.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StoryGenerateRequest(
        UUID childId,
        UUID sourceMomentId,
        String mainCharacterName,
        String secondCharacterName,
        @NotBlank String theme,
        String place,
        String favoriteAnimal,
        @NotNull StoryStyle style,
        @NotNull StoryLength length,
        StoryGenerationMode generationMode
) {}
