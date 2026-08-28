package com.rrsistemas.erauma.moment;

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
@Table(name = "moment_photo")
public class MomentPhoto {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moment_id")
    private Moment moment;
    @Column(name = "storage_key")
    private String storageKey;
    @Column(name = "original_filename")
    private String originalFilename;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "size_bytes")
    private long sizeBytes;
    @Column(name = "sort_order")
    private int sortOrder;
    @Column(name = "created_at")
    private Instant createdAt;
    private boolean active = true;

    protected MomentPhoto() {}

    public MomentPhoto(Moment moment, String storageKey, String originalFilename, String contentType, long sizeBytes, int sortOrder) {
        this.id = UUID.randomUUID();
        this.moment = moment;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Moment getMoment() { return moment; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }
    public void deactivate() { this.active = false; }
}
