package com.rrsistemas.erauma.child;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, UUID> {
    List<ChildProfile> findByFamily_IdAndActiveTrue(UUID familyId);
    Optional<ChildProfile> findByIdAndActiveTrue(UUID id);
}
