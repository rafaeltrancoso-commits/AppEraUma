package com.rrsistemas.erauma.moment;

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
@Table(name = "moment_participant")
public class MomentParticipant {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moment_id")
    private Moment moment;
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type")
    private ParticipantType participantType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @Column(name = "created_at")
    private Instant createdAt;

    protected MomentParticipant() {}

    public MomentParticipant(Moment moment, String name, ParticipantType participantType) {
        this.id = UUID.randomUUID();
        this.moment = moment;
        this.name = name.trim();
        this.participantType = participantType;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public ParticipantType getParticipantType() { return participantType; }
}
