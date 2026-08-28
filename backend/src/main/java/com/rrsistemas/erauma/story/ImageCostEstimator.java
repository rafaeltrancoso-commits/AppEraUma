package com.rrsistemas.erauma.story;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ImageCostEstimator {
    private final StoryImageProperties properties;

    public ImageCostEstimator(StoryImageProperties properties) {
        this.properties = properties;
    }

    public BigDecimal estimate(String quality) {
        return switch (quality == null ? "medium" : quality.toLowerCase()) {
            case "low" -> safe(properties.lowImageCostUsd());
            case "high" -> safe(properties.highImageCostUsd());
            default -> safe(properties.mediumImageCostUsd());
        };
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
