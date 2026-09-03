package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.moment.Moment;
import com.rrsistemas.erauma.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "story")
public class Story {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private ChildProfile child;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_moment_id")
    private Moment sourceMoment;
    private String title;
    private String summary;
    private String content;
    private String theme;
    private String place;
    @Column(name = "main_character_name")
    private String mainCharacterName;
    @Column(name = "second_character_name")
    private String secondCharacterName;
    @Column(name = "favorite_animal")
    private String favoriteAnimal;
    @Enumerated(EnumType.STRING)
    @Column(name = "story_style")
    private StoryStyle style;
    @Enumerated(EnumType.STRING)
    @Column(name = "story_length")
    private StoryLength length;
    private boolean favorite;
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type")
    private GenerationType generationType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    private boolean active = true;
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chapterNumber asc")
    private List<StoryChapter> chapters = new ArrayList<>();
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<StoryImage> images = new ArrayList<>();

    protected Story() {}

    public Story(Family family, ChildProfile child, Moment sourceMoment, StoryGenerateRequest request, GeneratedStory generated, AppUser createdBy) {
        this.id = UUID.randomUUID();
        this.family = family;
        this.child = child;
        this.sourceMoment = sourceMoment;
        this.title = generated.title();
        this.summary = StoryTextNormalizer.normalizeStoryText(generated.summary());
        this.content = StoryTextNormalizer.normalizeStoryText(generated.content());
        this.theme = request.theme().trim();
        this.place = blankToNull(request.place());
        this.mainCharacterName = blankToNull(request.mainCharacterName());
        this.secondCharacterName = blankToNull(request.secondCharacterName());
        this.favoriteAnimal = blankToNull(request.favoriteAnimal());
        this.style = request.style();
        this.length = request.length();
        this.generationType = generated.generationType();
        this.createdBy = createdBy;
        this.chapters = generated.chapters().stream().map(chapter -> new StoryChapter(this, chapter)).toList();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return family.getId(); }
    public Family getFamily() { return family; }
    public ChildProfile getChild() { return child; }
    public Moment getSourceMoment() { return sourceMoment; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public String getTheme() { return theme; }
    public String getPlace() { return place; }
    public String getMainCharacterName() { return mainCharacterName; }
    public String getSecondCharacterName() { return secondCharacterName; }
    public String getFavoriteAnimal() { return favoriteAnimal; }
    public StoryStyle getStyle() { return style; }
    public StoryLength getLength() { return length; }
    public boolean isFavorite() { return favorite; }
    public GenerationType getGenerationType() { return generationType; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return active; }
    public List<StoryChapter> getChapters() { return chapters; }
    public List<StoryImage> getImages() { return images; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public void setTitle(String title) { this.title = title.trim(); }
    public void deactivate() { this.active = false; }
}
