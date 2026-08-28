package com.rrsistemas.erauma.child;

import com.rrsistemas.erauma.family.Family;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "child_profile")
public class ChildProfile {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;
    private String name;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    private String nickname;
    @Column(name = "favorite_animal")
    private String favoriteAnimal;
    @Column(name = "avatar_url")
    private String avatarUrl;
    @Enumerated(EnumType.STRING)
    @Column(name = "visual_presentation")
    private VisualPresentation visualPresentation;
    @Enumerated(EnumType.STRING)
    @Column(name = "skin_tone")
    private SkinTone skinTone;
    @Column(name = "hair_color")
    private String hairColor;
    @Column(name = "hair_length")
    private String hairLength;
    @Enumerated(EnumType.STRING)
    @Column(name = "hair_texture")
    private HairTexture hairTexture;
    @Column(name = "eye_color")
    private String eyeColor;
    @Column(name = "special_features")
    private String specialFeatures;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    private boolean active = true;

    protected ChildProfile() {}

    public ChildProfile(Family family, ChildRequest request) {
        this.id = UUID.randomUUID();
        this.family = family;
        apply(request);
    }

    public void apply(ChildRequest request) {
        this.name = request.name().trim();
        this.birthDate = request.birthDate();
        this.nickname = request.nickname();
        this.favoriteAnimal = request.favoriteAnimal();
        this.avatarUrl = request.avatarUrl();
        this.visualPresentation = request.visualPresentation();
        this.skinTone = request.skinTone();
        this.hairColor = blankToNull(request.hairColor());
        this.hairLength = blankToNull(request.hairLength());
        this.hairTexture = request.hairTexture();
        this.eyeColor = blankToNull(request.eyeColor());
        this.specialFeatures = blankToNull(request.specialFeatures());
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return family.getId(); }
    public String getName() { return name; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getNickname() { return nickname; }
    public String getFavoriteAnimal() { return favoriteAnimal; }
    public String getAvatarUrl() { return avatarUrl; }
    public VisualPresentation getVisualPresentation() { return visualPresentation; }
    public SkinTone getSkinTone() { return skinTone; }
    public String getHairColor() { return hairColor; }
    public String getHairLength() { return hairLength; }
    public HairTexture getHairTexture() { return hairTexture; }
    public String getEyeColor() { return eyeColor; }
    public String getSpecialFeatures() { return specialFeatures; }
    public boolean isActive() { return active; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
