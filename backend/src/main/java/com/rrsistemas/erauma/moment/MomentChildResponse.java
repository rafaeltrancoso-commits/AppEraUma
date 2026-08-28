package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.child.ChildProfile;
import java.util.UUID;

public record MomentChildResponse(UUID id, String name, String nickname) {
    public static MomentChildResponse from(ChildProfile child) {
        return new MomentChildResponse(child.getId(), child.getName(), child.getNickname());
    }
}
