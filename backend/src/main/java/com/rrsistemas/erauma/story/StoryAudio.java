package com.rrsistemas.erauma.story;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_audio")
public class StoryAudio {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "story_id") private Story story;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "chapter_id") private StoryChapter chapter;
    @Column(name = "chunk_index") private int chunkIndex;
    @Column(name = "text_hash") private String textHash;
    private String voice;
    private String model;
    @Column(name = "audio_format") private String audioFormat;
    @Column(name = "storage_key") private String storageKey;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Enumerated(EnumType.STRING) private StoryAudioStatus status;
    @Column(name = "attempt_count") private int attemptCount;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    protected StoryAudio() {}

    public StoryAudio(Story story, StoryChapter chapter, int chunkIndex, String textHash,
            String voice, String model, String audioFormat) {
        this.id = UUID.randomUUID();
        this.story = story;
        this.chapter = chapter;
        this.chunkIndex = chunkIndex;
        this.textHash = textHash;
        this.voice = voice;
        this.model = model;
        this.audioFormat = audioFormat;
        this.status = StoryAudioStatus.PENDING;
    }

    @PrePersist void prePersist() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public void start() { status = StoryAudioStatus.PROCESSING; attemptCount++; errorMessage = null; }
    public void complete(String key, long bytes) { storageKey = key; sizeBytes = bytes; status = StoryAudioStatus.COMPLETED; errorMessage = null; }
    public void fail(String message) {
        status = StoryAudioStatus.FAILED;
        errorMessage = message == null ? "Falha ao gerar narração." : message.substring(0, Math.min(500, message.length()));
    }
    public void retry() { status = StoryAudioStatus.PENDING; errorMessage = null; }

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public StoryChapter getChapter() { return chapter; }
    public int getChunkIndex() { return chunkIndex; }
    public String getTextHash() { return textHash; }
    public String getVoice() { return voice; }
    public String getModel() { return model; }
    public String getAudioFormat() { return audioFormat; }
    public String getStorageKey() { return storageKey; }
    public Long getSizeBytes() { return sizeBytes; }
    public StoryAudioStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getUpdatedAt() { return updatedAt; }
}
