package com.rrsistemas.erauma.story;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_chapter")
public class StoryChapter {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;
    @Column(name = "chapter_number")
    private int chapterNumber;
    private String title;
    private String content;
    @Column(name = "created_at")
    private Instant createdAt;

    protected StoryChapter() {}

    public StoryChapter(Story story, GeneratedChapter chapter) {
        this.id = UUID.randomUUID();
        this.story = story;
        this.chapterNumber = chapter.number();
        this.title = chapter.title();
        this.content = StoryTextNormalizer.normalizeStoryText(chapter.content());
    }

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public int getChapterNumber() { return chapterNumber; }
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return StoryTextNormalizer.normalizeStoryText(content); }
}
