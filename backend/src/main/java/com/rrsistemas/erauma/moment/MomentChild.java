package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.child.ChildProfile;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "moment_child")
public class MomentChild {
    @EmbeddedId
    private MomentChildId id;
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("momentId")
    @JoinColumn(name = "moment_id")
    private Moment moment;
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("childId")
    @JoinColumn(name = "child_id")
    private ChildProfile child;
    @Column(name = "created_at")
    private Instant createdAt;

    protected MomentChild() {}

    public MomentChild(Moment moment, ChildProfile child) {
        this.id = new MomentChildId(moment.getId(), child.getId());
        this.moment = moment;
        this.child = child;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public ChildProfile getChild() { return child; }
}
