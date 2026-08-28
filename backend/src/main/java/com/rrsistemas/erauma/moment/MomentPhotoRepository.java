package com.rrsistemas.erauma.moment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MomentPhotoRepository extends JpaRepository<MomentPhoto, UUID> {
    Optional<MomentPhoto> findByIdAndActiveTrue(UUID id);
    long countByMoment_IdAndActiveTrue(UUID momentId);
    List<MomentPhoto> findByMoment_IdAndActiveTrueOrderBySortOrderAsc(UUID momentId);
}
