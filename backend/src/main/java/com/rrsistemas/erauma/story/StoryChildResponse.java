package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.child.ChildProfile;
import java.util.UUID;

public record StoryChildResponse(UUID id, String name, String nickname) {
    static StoryChildResponse from(ChildProfile child) {
        if (child == null) {
            return null;
        }
        return new StoryChildResponse(child.getId(), child.getName(), child.getNickname());
    }
}
