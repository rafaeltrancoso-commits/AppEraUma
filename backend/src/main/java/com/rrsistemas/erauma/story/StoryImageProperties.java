package com.rrsistemas.erauma.story;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.story.image")
public record StoryImageProperties(
        boolean generationEnabled,
        int maxImages,
        int maxAttempts,
        BigDecimal mediumImageCostUsd,
        BigDecimal lowImageCostUsd,
        BigDecimal highImageCostUsd
) {
    public int effectiveMaxAttempts() {
        return maxAttempts > 0 ? maxAttempts : 3;
    }
}
