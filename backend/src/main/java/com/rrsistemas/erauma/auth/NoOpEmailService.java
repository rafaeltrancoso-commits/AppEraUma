package com.rrsistemas.erauma.auth;

import com.rrsistemas.erauma.user.AppUser;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpEmailService implements EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpEmailService.class);

    @Override
    public void sendPasswordReset(AppUser user, String token, Instant expiresAt) {
        LOGGER.info("password_reset_email_generated userId={} expiresAt={}", user.getId(), expiresAt);
    }
}
