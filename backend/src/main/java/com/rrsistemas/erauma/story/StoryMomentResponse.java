package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.moment.Moment;
import java.util.UUID;

public record StoryMomentResponse(UUID id, String title) {
    static StoryMomentResponse from(Moment moment) {
        return moment == null ? null : new StoryMomentResponse(moment.getId(), moment.getTitle());
    }
}
