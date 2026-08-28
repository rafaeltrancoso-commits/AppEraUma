package com.rrsistemas.erauma.child;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record ChildRequest(
        @NotBlank String name,
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
) {}
