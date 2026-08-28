package com.rrsistemas.erauma.story;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai.image")
public record OpenAiImageProperties(
        String model,
        String size,
        String quality,
        int timeoutSeconds
) {}
