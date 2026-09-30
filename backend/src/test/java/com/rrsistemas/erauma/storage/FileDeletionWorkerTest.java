package com.rrsistemas.erauma.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rrsistemas.erauma.moment.FileStorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FileDeletionWorkerTest {
    @Test
    void failedDeletionRemainsRetryableAndSecondAttemptCompletes() {
        FileDeletionJob job = new FileDeletionJob(FileDeletionType.MOMENT_PHOTO, "photo-key");
        FileDeletionJobRepository jobs = mock(FileDeletionJobRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(jobs.findTop100ByCompletedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(job));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("volume unavailable"))
                .doNothing()
                .when(storage).deleteMomentPhoto("photo-key");
        FileDeletionWorker worker = new FileDeletionWorker(jobs, storage, NOOP_TRANSACTIONS);

        worker.processPendingJobs();
        assertThat(job.isCompleted()).isFalse();
        assertThat(job.getAttemptCount()).isEqualTo(1);

        worker.processPendingJobs();
        assertThat(job.isCompleted()).isTrue();
        verify(storage, org.mockito.Mockito.times(2)).deleteMomentPhoto("photo-key");
    }

    @Test
    void deletesStoryAudioThroughThePersistentQueue() {
        FileDeletionJob job = new FileDeletionJob(FileDeletionType.STORY_AUDIO, "story/audio.mp3");
        FileDeletionJobRepository jobs = mock(FileDeletionJobRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(jobs.findTop100ByCompletedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(job));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        new FileDeletionWorker(jobs, storage, NOOP_TRANSACTIONS).processPendingJobs();

        assertThat(job.isCompleted()).isTrue();
        verify(storage).deleteStoryAudio("story/audio.mp3");
    }

    private static final PlatformTransactionManager NOOP_TRANSACTIONS = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override public void commit(TransactionStatus status) {}
        @Override public void rollback(TransactionStatus status) {}
    };
}
