package com.rrsistemas.erauma.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
        StoryGenerationMode generationMode,
        @Size(max = 3) List<UUID> characterIds,
        @Size(max = 500) String otherCharacters,
        @Size(max = 120) String idempotencyKey
) {
    public StoryGenerateRequest(UUID childId, UUID sourceMomentId, String mainCharacterName,
            String secondCharacterName, String theme, String place, String favoriteAnimal,
            StoryStyle style, StoryLength length, StoryGenerationMode generationMode) {
        this(childId, sourceMomentId, mainCharacterName, secondCharacterName, theme, place,
                favoriteAnimal, style, length, generationMode, null, secondCharacterName, null);
    }
}
