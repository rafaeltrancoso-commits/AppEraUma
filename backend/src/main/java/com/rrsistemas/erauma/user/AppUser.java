package com.rrsistemas.erauma.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;
    private String name;
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;
    private boolean active = true;

    protected AppUser() {}

    public AppUser(String name, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
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

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public boolean isDeletionRequested() { return deletionRequestedAt != null; }
    public boolean isAvailable() { return active && deletionRequestedAt == null; }
    public void requestDeletion() { deletionRequestedAt = Instant.now(); }
    public void cancelDeletion() { deletionRequestedAt = null; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
