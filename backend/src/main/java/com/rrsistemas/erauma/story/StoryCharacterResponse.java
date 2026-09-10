package com.rrsistemas.erauma.story;

import java.time.LocalDate;
import java.util.UUID;

public record StoryCharacterResponse(
        UUID id,
        String name,
        String nickname,
        LocalDate birthDate,
        int selectionOrder,
        StoryCharacterRole role,
        String visualDescription
) {
    static StoryCharacterResponse from(StoryCharacter item) {
        var character = item.getCharacter();
        return new StoryCharacterResponse(character.getId(), character.getName(), character.getNickname(), character.getBirthDate(), item.getSelectionOrder(), item.getRole(), item.getVisualDescription());
    }
}
