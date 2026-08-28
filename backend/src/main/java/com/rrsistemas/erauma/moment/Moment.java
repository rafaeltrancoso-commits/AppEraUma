package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "moment")
public class Moment {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;
    private String title;
    private String description;
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;
    @Column(name = "location_name")
    private String locationName;
    private boolean favorite;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    private boolean active = true;
    @OneToMany(mappedBy = "moment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MomentChild> children = new ArrayList<>();
    @OneToMany(mappedBy = "moment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MomentParticipant> participants = new ArrayList<>();
    @OneToMany(mappedBy = "moment")
    @OrderBy("sortOrder asc")
    private List<MomentPhoto> photos = new ArrayList<>();

    protected Moment() {}

    public Moment(Family family, AppUser createdBy, MomentRequest request) {
        this.id = UUID.randomUUID();
        this.family = family;
        this.createdBy = createdBy;
        apply(request);
    }

    public void apply(MomentRequest request) {
        this.title = request.title().trim();
        this.description = blankToNull(request.description());
        this.occurredAt = request.occurredAt();
        this.locationName = blankToNull(request.locationName());
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public Family getFamily() { return family; }
    public UUID getFamilyId() { return family.getId(); }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getLocationName() { return locationName; }
    public boolean isFavorite() { return favorite; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return active; }
    public List<MomentChild> getChildren() { return children; }
    public List<MomentParticipant> getParticipants() { return participants; }
    public List<MomentPhoto> getPhotos() { return photos; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public void deactivate() { this.active = false; }
    public void replaceChildren(List<MomentChild> children) { this.children.clear(); this.children.addAll(children); }
    public void replaceParticipants(List<MomentParticipant> participants) { this.participants.clear(); this.participants.addAll(participants); }
    public void clearRelations() { this.children.clear(); this.participants.clear(); }
    public void addChildren(List<MomentChild> children) { this.children.addAll(children); }
    public void addParticipants(List<MomentParticipant> participants) { this.participants.addAll(participants); }
}
