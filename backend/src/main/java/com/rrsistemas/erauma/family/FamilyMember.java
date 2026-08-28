package com.rrsistemas.erauma.family;

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
@Table(name = "family_member")
public class FamilyMember {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @Enumerated(EnumType.STRING)
    private FamilyMemberRole role;
    @Column(name = "created_at")
    private Instant createdAt;
    private boolean active = true;

    protected FamilyMember() {}

    public FamilyMember(Family family, AppUser user, FamilyMemberRole role) {
        this.id = UUID.randomUUID();
        this.family = family;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public FamilyMemberRole getRole() { return role; }
}

