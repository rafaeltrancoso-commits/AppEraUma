package com.rrsistemas.erauma.story;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String model,
        int timeoutSeconds
) {}
