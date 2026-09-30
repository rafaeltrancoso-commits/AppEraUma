package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.shared.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/features")
public class AppFeatureController {
    private final StoryAudioAuthorizationService authorization;
    private final CurrentUser currentUser;

    public AppFeatureController(StoryAudioAuthorizationService authorization, CurrentUser currentUser) {
        this.authorization = authorization;
        this.currentUser = currentUser;
    }

    @GetMapping
    AppFeatureResponse features() {
        return new AppFeatureResponse(authorization.isAvailable(currentUser.get()));
    }
}
