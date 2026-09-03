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
@Table(name = "story_image")
public class StoryImage {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private StoryChapter chapter;
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type")
    private StoryImageType imageType;
    @Column(name = "storage_key")
    private String storageKey;
    private String model;
    private String size;
    private String quality;
    @Column(name = "sort_order")
    private int sortOrder;
    @Column(name = "chapter_start")
    private Integer chapterStart;
    @Column(name = "chapter_end")
    private Integer chapterEnd;
    @Column(name = "prompt_text")
    private String promptText;
    @Column(name = "error_message")
    private String errorMessage;
    @Enumerated(EnumType.STRING)
    private StoryImageStatus status;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected StoryImage() {}

    public StoryImage(Story story, StoryChapter chapter, StoryImageType imageType, String model, String size, String quality, int sortOrder) {
        this(story, chapter, imageType, model, size, quality, sortOrder, null, null, null);
    }

    public StoryImage(Story story, StoryChapter chapter, StoryImageType imageType, String model, String size, String quality, int sortOrder, Integer chapterStart, Integer chapterEnd, String promptText) {
        this.id = UUID.randomUUID();
        this.story = story;
        this.chapter = chapter;
        this.imageType = imageType;
        this.model = model;
        this.size = size;
        this.quality = quality;
        this.sortOrder = sortOrder;
        this.chapterStart = chapterStart;
        this.chapterEnd = chapterEnd;
        this.promptText = promptText;
        this.status = StoryImageStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public void markGenerated(String storageKey) {
        this.storageKey = storageKey;
        this.status = StoryImageStatus.GENERATED;
    }

    public void markGenerated(String storageKey, String model, String size, String quality) {
        this.storageKey = storageKey;
        this.model = model;
        this.size = size;
        this.quality = quality;
        this.errorMessage = null;
        this.status = StoryImageStatus.GENERATED;
    }

    public void markGenerating() {
        this.status = StoryImageStatus.GENERATING;
        this.errorMessage = null;
    }
    public void markFailed() { this.status = StoryImageStatus.FAILED; }
    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = StoryImageStatus.FAILED;
    }
    public void updatePlan(Integer chapterStart, Integer chapterEnd, String promptText) {
        this.chapterStart = chapterStart;
        this.chapterEnd = chapterEnd;
        this.promptText = promptText;
    }
    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public StoryChapter getChapter() { return chapter; }
    public StoryImageType getImageType() { return imageType; }
    public String getStorageKey() { return storageKey; }
    public String getModel() { return model; }
    public String getSize() { return size; }
    public String getQuality() { return quality; }
    public int getSortOrder() { return sortOrder; }
    public Integer getChapterStart() { return chapterStart; }
    public Integer getChapterEnd() { return chapterEnd; }
    public String getPromptText() { return promptText; }
    public String getErrorMessage() { return errorMessage; }
    public StoryImageStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
