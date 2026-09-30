package com.rrsistemas.erauma.storage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileDeletionQueueService {
    private final FileDeletionJobRepository jobs;

    public FileDeletionQueueService(FileDeletionJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public void enqueue(FileDeletionType type, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        if (jobs.findByStorageTypeAndStorageKey(type, storageKey).isEmpty()) {
            jobs.save(new FileDeletionJob(type, storageKey));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueStoryImageAfterRace(String storageKey) {
        enqueue(FileDeletionType.STORY_IMAGE, storageKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueStoryAudioAfterRace(String storageKey) {
        enqueue(FileDeletionType.STORY_AUDIO, storageKey);
    }
}
