package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.storage.FileDeletionQueueService;
import com.rrsistemas.erauma.user.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class StoryAudioGenerationServiceTest {
    @Test
    void completedVersionIsReusedWithoutCallingProviderOrCreatingAnotherRow() {
        Fixture fixture = new Fixture();
        Story story = mock(Story.class);
        StoryChapter chapter = mock(StoryChapter.class);
        AppUser user = mock(AppUser.class);
        when(story.getId()).thenReturn(java.util.UUID.randomUUID());
        when(story.getGenerationStatus()).thenReturn(StoryGenerationStatus.CONCLUIDA);
        when(story.getChapters()).thenReturn(List.of(chapter));
        when(chapter.getId()).thenReturn(java.util.UUID.randomUUID());
        when(chapter.getChapterNumber()).thenReturn(1);
        when(chapter.getContent()).thenReturn("Texto aprovado para narrar.");
        StoryAudio existing = new StoryAudio(story, chapter, 0,
                StoryAudioText.hash(chapter.getContent()), "marin", "gpt-4o-mini-tts", "mp3");
        existing.complete("story/audio.mp3", 10);
        when(fixture.stories.findForGeneration(story.getId())).thenReturn(Optional.of(story));
        when(fixture.audios.findByChapter_IdAndTextHashAndVoiceAndChunkIndex(
                chapter.getId(), StoryAudioText.hash(chapter.getContent()), "marin", 0)).thenReturn(Optional.of(existing));
        when(fixture.audios.findByStory_IdOrderByChapter_ChapterNumberAscChunkIndexAsc(story.getId()))
                .thenReturn(List.of(existing));

        StoryNarrationResponse response = fixture.service().request(story.getId(), user);

        assertThat(response.status()).isEqualTo(StoryNarrationStatus.COMPLETED);
        verify(fixture.audios, never()).save(any());
        verify(fixture.generator, never()).generate(any());
    }

    @Test
    void providerFailurePersistsFailedStateAndAllowsRetryTransition() {
        Fixture fixture = new Fixture();
        Story story = mock(Story.class);
        StoryChapter chapter = mock(StoryChapter.class);
        when(story.getId()).thenReturn(java.util.UUID.randomUUID());
        when(story.isActive()).thenReturn(true);
        when(chapter.getContent()).thenReturn("Texto aprovado para narrar.");
        StoryAudio audio = new StoryAudio(story, chapter, 0,
                StoryAudioText.hash(chapter.getContent()), "marin", "gpt-4o-mini-tts", "mp3");
        when(fixture.audios.findForUpdate(audio.getId())).thenReturn(Optional.of(audio));
        when(fixture.generator.generate(any())).thenThrow(new AiUnavailableException("indisponível"));

        fixture.service().process(audio.getId());

        assertThat(audio.getStatus()).isEqualTo(StoryAudioStatus.FAILED);
        assertThat(audio.getAttemptCount()).isEqualTo(1);
        audio.retry();
        assertThat(audio.getStatus()).isEqualTo(StoryAudioStatus.PENDING);
    }

    private static class Fixture {
        final StoryRepository stories = mock(StoryRepository.class);
        final StoryAudioRepository audios = mock(StoryAudioRepository.class);
        final StoryAudioAuthorizationService authorization = mock(StoryAudioAuthorizationService.class);
        final StoryAudioGenerator generator = mock(StoryAudioGenerator.class);
        final FileStorageService storage = mock(FileStorageService.class);
        final FileDeletionQueueService deletion = mock(FileDeletionQueueService.class);
        final Executor executor = Runnable::run;

        StoryAudioGenerationService service() {
            return new StoryAudioGenerationService(stories, audios, authorization,
                    new StoryAudioProperties(true, 3, 4000),
                    new OpenAiAudioProperties("gpt-4o-mini-tts", "marin", "mp3", 2, "Preserve o texto."),
                    generator, storage, deletion, NOOP_TRANSACTIONS, executor);
        }
    }

    private static final PlatformTransactionManager NOOP_TRANSACTIONS = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) {}
        @Override public void rollback(TransactionStatus status) {}
    };
}
