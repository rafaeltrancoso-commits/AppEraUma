package com.rrsistemas.erauma.auth;

import com.rrsistemas.erauma.user.AppUser;
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
@Table(name = "password_reset_token")
public class PasswordResetToken {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "created_at")
    private Instant createdAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(AppUser user, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public AppUser getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void markUsed() { usedAt = Instant.now(); }
    public boolean isUsable(Instant now) { return usedAt == null && expiresAt.isAfter(now); }
}
