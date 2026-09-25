package com.rrsistemas.erauma.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_deletion_job")
public class FileDeletionJob {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type")
    private FileDeletionType storageType;
    @Column(name = "storage_key")
    private String storageKey;
    @Column(name = "attempt_count")
    private int attemptCount;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected FileDeletionJob() {}

    public FileDeletionJob(FileDeletionType storageType, String storageKey) {
        this.id = UUID.randomUUID();
        this.storageType = storageType;
        this.storageKey = storageKey;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public FileDeletionType getStorageType() { return storageType; }
    public String getStorageKey() { return storageKey; }
    public int getAttemptCount() { return attemptCount; }
    public boolean isCompleted() { return completedAt != null; }
    public void complete() { completedAt = Instant.now(); lastError = null; }
    public void fail(String error) {
        attemptCount += 1;
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500));
    }
}
