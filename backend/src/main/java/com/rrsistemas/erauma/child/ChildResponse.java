package com.rrsistemas.erauma.child;

import java.time.LocalDate;
import java.util.UUID;

public record ChildResponse(
        UUID id,
        UUID familyId,
        String name,
        LocalDate birthDate,
        String nickname,
        String favoriteAnimal,
        String avatarUrl,
        VisualPresentation visualPresentation,
        SkinTone skinTone,
        String hairColor,
        String hairLength,
        HairTexture hairTexture,
        String eyeColor,
        String specialFeatures
) {
    public static ChildResponse from(ChildProfile child) {
        return new ChildResponse(
                child.getId(),
                child.getFamilyId(),
                child.getName(),
                child.getBirthDate(),
                child.getNickname(),
                child.getFavoriteAnimal(),
                child.getAvatarUrl(),
                child.getVisualPresentation(),
                child.getSkinTone(),
                child.getHairColor(),
                child.getHairLength(),
                child.getHairTexture(),
                child.getEyeColor(),
                child.getSpecialFeatures());
    }
}
