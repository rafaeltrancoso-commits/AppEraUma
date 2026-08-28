package com.rrsistemas.erauma.family;

import java.util.UUID;

public record FamilyResponse(UUID id, String name) {
    public static FamilyResponse from(Family family) {
        return new FamilyResponse(family.getId(), family.getName());
    }
}

