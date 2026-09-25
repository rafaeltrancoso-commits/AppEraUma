package com.rrsistemas.erauma.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileDeletionJobRepository extends JpaRepository<FileDeletionJob, UUID> {
    List<FileDeletionJob> findTop100ByCompletedAtIsNullOrderByCreatedAtAsc();
    Optional<FileDeletionJob> findByStorageTypeAndStorageKey(FileDeletionType storageType, String storageKey);
}
