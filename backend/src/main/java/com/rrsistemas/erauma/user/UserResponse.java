package com.rrsistemas.erauma.user;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}

