package com.rrsistemas.erauma.story;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.story.image")
public record StoryImageProperties(
        boolean generationEnabled,
        int maxImages,
        BigDecimal mediumImageCostUsd,
        BigDecimal lowImageCostUsd,
        BigDecimal highImageCostUsd
) {}
