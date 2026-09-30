package com.rrsistemas.erauma.story;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai.audio")
public record OpenAiAudioProperties(String model, String voice, String format, int timeoutSeconds, String instructions) {}
