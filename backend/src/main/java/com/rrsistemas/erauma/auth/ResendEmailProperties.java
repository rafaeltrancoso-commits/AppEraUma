package com.rrsistemas.erauma.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record ResendEmailProperties(
        String resendApiKey,
        String from,
        String passwordResetUrl,
        int timeoutSeconds
) {}
