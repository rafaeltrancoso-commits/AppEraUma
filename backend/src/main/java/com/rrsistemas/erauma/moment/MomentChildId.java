package com.rrsistemas.erauma.moment;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MomentChildId implements Serializable {
    private UUID momentId;
    private UUID childId;

    protected MomentChildId() {}

    public MomentChildId(UUID momentId, UUID childId) {
        this.momentId = momentId;
        this.childId = childId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MomentChildId that)) {
            return false;
        }
        return Objects.equals(momentId, that.momentId) && Objects.equals(childId, that.childId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(momentId, childId);
    }
}
