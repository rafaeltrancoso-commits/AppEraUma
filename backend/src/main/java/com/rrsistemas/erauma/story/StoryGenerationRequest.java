package com.rrsistemas.erauma.story;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StoryGenerationRequest(
        UUID familyId,
        UUID childId,
        String childName,
        LocalDate childBirthDate,
        String mainCharacterName,
        String secondCharacterName,
        UUID sourceMomentId,
        String sourceMomentTitle,
        String sourceMomentDescription,
        String sourceMomentLocation,
        String theme,
        String place,
        String favoriteAnimal,
        StoryStyle style,
        StoryLength length,
        List<StoryCharacterPrompt> characters,
        String otherCharacters
) {
    public StoryGenerationRequest(UUID familyId, UUID childId, String childName, LocalDate childBirthDate,
            String mainCharacterName, String secondCharacterName, UUID sourceMomentId, String sourceMomentTitle,
            String sourceMomentDescription, String sourceMomentLocation, String theme, String place,
            String favoriteAnimal, StoryStyle style, StoryLength length) {
        this(familyId, childId, childName, childBirthDate, mainCharacterName, secondCharacterName,
                sourceMomentId, sourceMomentTitle, sourceMomentDescription, sourceMomentLocation, theme,
                place, favoriteAnimal, style, length, List.of(), secondCharacterName);
    }
}
