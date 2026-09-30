package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StoryAudioAuthorizationService {
    private final StoryAudioProperties properties;
    private final FamilyService families;

    public StoryAudioAuthorizationService(StoryAudioProperties properties, FamilyService families) {
        this.properties = properties;
        this.families = families;
    }

    public boolean isAvailable(AppUser user) {
        return properties.enabled() && user != null && user.isAvailable();
    }

    public void requireAvailable(AppUser user) {
        if (!isAvailable(user)) {
            throw new BusinessException("AI_AUDIO_DISABLED", "Narração por IA indisponível.", HttpStatus.NOT_FOUND);
        }
    }

    public void requireStoryAccess(Story story, AppUser user) {
        requireAvailable(user);
        families.requireMembership(story.getFamilyId(), user);
    }
}
