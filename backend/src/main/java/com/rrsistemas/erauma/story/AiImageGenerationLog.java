package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_image_generation_log")
public class AiImageGenerationLog {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_image_id")
    private StoryImage storyImage;
    private String provider;
    private String model;
    private String quality;
    private String size;
    @Enumerated(EnumType.STRING)
    private StoryImageStatus status;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(name = "estimated_cost_usd")
    private BigDecimal estimatedCostUsd;
    @Column(name = "created_at")
    private Instant createdAt;

    protected AiImageGenerationLog() {}

    public AiImageGenerationLog(AppUser user, Family family, Story story, StoryImage storyImage, String provider, String model, String quality, String size, StoryImageStatus status, Long durationMs, BigDecimal estimatedCostUsd) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.family = family;
        this.story = story;
        this.storyImage = storyImage;
        this.provider = provider;
        this.model = model;
        this.quality = quality;
        this.size = size;
        this.status = status;
        this.durationMs = durationMs;
        this.estimatedCostUsd = estimatedCostUsd;
    }

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }
}
