package com.rrsistemas.erauma.auth;

import com.rrsistemas.erauma.user.AppUser;
import java.time.Instant;

public interface EmailService {
    void sendPasswordReset(AppUser user, String token, Instant expiresAt);
}
