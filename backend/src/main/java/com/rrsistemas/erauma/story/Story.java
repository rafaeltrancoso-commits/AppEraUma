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
    @Column(name = "other_characters")
    private String otherCharacters;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status")
    private StoryGenerationStatus generationStatus = StoryGenerationStatus.CONCLUIDA;
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_mode")
    private StoryGenerationMode generationMode = StoryGenerationMode.TEXT_ONLY;
    @Column(name = "generation_error")
    private String generationError;
    @Column(name = "generation_attempt_count")
    private int generationAttemptCount;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chapterNumber asc")
    private List<StoryChapter> chapters = new ArrayList<>();
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<StoryImage> images = new ArrayList<>();
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("selectionOrder asc")
    private List<StoryCharacter> characters = new ArrayList<>();

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
        this.otherCharacters = blankToNull(request.otherCharacters());
        this.favoriteAnimal = blankToNull(request.favoriteAnimal());
        this.style = request.style();
        this.length = request.length();
        this.generationType = generated.generationType();
        this.generationMode = request.generationMode() == null ? StoryGenerationMode.TEXT_ONLY : request.generationMode();
        this.idempotencyKey = blankToNull(request.idempotencyKey());
        this.createdBy = createdBy;
        this.chapters = generated.chapters().stream().map(chapter -> new StoryChapter(this, chapter)).toList();
    }

    public static Story pending(Family family, ChildProfile protagonist, Moment sourceMoment, StoryGenerateRequest request, AppUser createdBy) {
        Story story = new Story();
        story.id = UUID.randomUUID();
        story.family = family;
        story.child = protagonist;
        story.sourceMoment = sourceMoment;
        story.title = "Sua história está sendo preparada";
        story.summary = "";
        story.content = "";
        story.theme = request.theme().trim();
        story.place = story.blankToNull(request.place());
        story.mainCharacterName = story.blankToNull(request.mainCharacterName());
        if (story.mainCharacterName == null && protagonist != null) story.mainCharacterName = story.firstName(protagonist.getName());
        story.secondCharacterName = story.blankToNull(request.secondCharacterName());
        story.otherCharacters = story.blankToNull(request.otherCharacters());
        story.favoriteAnimal = story.blankToNull(request.favoriteAnimal());
        story.style = request.style();
        story.length = request.length();
        story.generationType = GenerationType.AI;
        story.generationMode = request.generationMode() == null ? StoryGenerationMode.TEXT_ONLY : request.generationMode();
        story.generationStatus = StoryGenerationStatus.PENDENTE;
        story.idempotencyKey = story.blankToNull(request.idempotencyKey());
        story.createdBy = createdBy;
        story.createdAt = Instant.now();
        story.updatedAt = story.createdAt;
        return story;
    }

    public void addCharacter(ChildProfile character, int order, String visualDescription) {
        characters.add(new StoryCharacter(this, character, order, visualDescription));
    }

    public void markProcessingText() {
        generationStatus = StoryGenerationStatus.PROCESSANDO_TEXTO;
        generationError = null;
        generationAttemptCount += 1;
    }
    public void completeText(GeneratedStory generated) {
        title = generated.title();
        summary = StoryTextNormalizer.normalizeStoryText(generated.summary());
        content = StoryTextNormalizer.normalizeStoryText(generated.content());
        generationType = generated.generationType();
        chapters.clear();
        chapters.addAll(generated.chapters().stream().map(chapter -> new StoryChapter(this, chapter)).toList());
        generationStatus = generationMode == StoryGenerationMode.ILLUSTRATED ? StoryGenerationStatus.PROCESSANDO_IMAGENS : StoryGenerationStatus.CONCLUIDA;
        generationError = null;
    }
    public void markGenerationFailed(String message) { generationStatus = StoryGenerationStatus.ERRO; generationError = message; }
    public void retryGeneration() { generationStatus = StoryGenerationStatus.PENDENTE; generationError = null; }
    public void updateGenerationFromImages(StoryGenerationStatus status) { generationStatus = status; }
    public void markProcessingImages() { generationStatus = StoryGenerationStatus.PROCESSANDO_IMAGENS; }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String firstName(String value) { String clean = value.trim(); int space = clean.indexOf(' '); return space > 0 ? clean.substring(0, space) : clean; }

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
    public String getOtherCharacters() { return otherCharacters; }
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
    public List<StoryCharacter> getCharacters() { return characters; }
    public StoryGenerationStatus getGenerationStatus() { return generationStatus; }
    public StoryGenerationMode getGenerationMode() { return generationMode; }
    public String getGenerationError() { return generationError; }
    public int getGenerationAttemptCount() { return generationAttemptCount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public void setTitle(String title) { this.title = title.trim(); }
    public void deactivate() { this.active = false; }
}
