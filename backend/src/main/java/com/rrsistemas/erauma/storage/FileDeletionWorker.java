package com.rrsistemas.erauma.storage;

import com.rrsistemas.erauma.moment.FileStorageService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FileDeletionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileDeletionWorker.class);
    private final FileDeletionJobRepository jobs;
    private final FileStorageService storage;
    private final TransactionTemplate transactions;

    public FileDeletionWorker(FileDeletionJobRepository jobs, FileStorageService storage,
            PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.storage.deletion-worker-delay-ms:60000}")
    public void processPendingJobs() {
        for (FileDeletionJob snapshot : jobs.findTop100ByCompletedAtIsNullOrderByCreatedAtAsc()) {
            processOne(snapshot.getId());
        }
    }

    private void processOne(UUID id) {
        transactions.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> {
            try {
                switch (job.getStorageType()) {
                    case MOMENT_PHOTO -> storage.deleteMomentPhoto(job.getStorageKey());
                    case STORY_IMAGE -> storage.deleteStoryImage(job.getStorageKey());
                    case STORY_AUDIO -> storage.deleteStoryAudio(job.getStorageKey());
                    case STORY_DIRECTORY -> storage.deleteStoryDirectory(UUID.fromString(job.getStorageKey()));
                }
                job.complete();
            } catch (RuntimeException exception) {
                job.fail(exception.getMessage());
                LOGGER.warn("file_deletion_failed jobId={} type={} reason={}", job.getId(),
                        job.getStorageType(), exception.getClass().getSimpleName());
            }
        }));
    }
}
