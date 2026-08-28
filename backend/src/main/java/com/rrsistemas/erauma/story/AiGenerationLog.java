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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_generation_log")
public class AiGenerationLog {
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
    private String provider;
    private String model;
    @Enumerated(EnumType.STRING)
    private AiGenerationStatus status;
    @Column(name = "input_tokens")
    private Integer inputTokens;
    @Column(name = "output_tokens")
    private Integer outputTokens;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(name = "created_at")
    private Instant createdAt;

    protected AiGenerationLog() {}

    public AiGenerationLog(AppUser user, Family family, Story story, GeneratedStory generated, AiGenerationStatus status) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.family = family;
        this.story = story;
        this.provider = generated.provider();
        this.model = generated.model();
        this.status = status;
        this.inputTokens = generated.inputTokens();
        this.outputTokens = generated.outputTokens();
        this.durationMs = generated.durationMs();
    }

    public AiGenerationLog(AppUser user, Family family, String provider, String model, AiGenerationStatus status, Long durationMs) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.family = family;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.durationMs = durationMs;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
