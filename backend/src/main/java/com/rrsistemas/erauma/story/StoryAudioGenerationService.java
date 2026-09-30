package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.storage.FileDeletionQueueService;
import com.rrsistemas.erauma.storage.FileDeletionType;
import com.rrsistemas.erauma.user.AppUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StoryAudioGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAudioGenerationService.class);
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private final StoryRepository stories;
    private final StoryAudioRepository audios;
    private final StoryAudioAuthorizationService authorization;
    private final StoryAudioProperties properties;
    private final OpenAiAudioProperties openAiAudio;
    private final StoryAudioGenerator generator;
    private final FileStorageService storage;
    private final FileDeletionQueueService deletionQueue;
    private final Executor executor;
    private final TransactionTemplate transactions;
    private final Set<UUID> active = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public StoryAudioGenerationService(StoryRepository stories, StoryAudioRepository audios,
            StoryAudioAuthorizationService authorization, StoryAudioProperties properties,
            OpenAiAudioProperties openAiAudio, StoryAudioGenerator generator, FileStorageService storage,
            FileDeletionQueueService deletionQueue, PlatformTransactionManager transactionManager,
            @Qualifier("storyAudioExecutor") Executor executor) {
        this.stories = stories;
        this.audios = audios;
        this.authorization = authorization;
        this.properties = properties;
        this.openAiAudio = openAiAudio;
        this.generator = generator;
        this.storage = storage;
        this.deletionQueue = deletionQueue;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public StoryNarrationResponse get(UUID storyId, AppUser user) {
        Story story = requireStory(storyId);
        authorization.requireStoryAccess(story, user);
        return response(storyId);
    }

    @Transactional
    public StoryNarrationResponse request(UUID storyId, AppUser user) {
        Story story = stories.findForGeneration(storyId)
                .orElseThrow(() -> notFound());
        authorization.requireStoryAccess(story, user);
        if (story.getGenerationStatus() != StoryGenerationStatus.CONCLUIDA
                && story.getGenerationStatus() != StoryGenerationStatus.CONCLUIDA_COM_FALHAS) {
            throw new BusinessException("STORY_AUDIO_NOT_READY", "A história precisa estar concluída antes da narração.", HttpStatus.CONFLICT);
        }
        if (story.getChapters().isEmpty()) {
            throw new BusinessException("STORY_AUDIO_TEXT_EMPTY", "A história não possui texto para narrar.", HttpStatus.BAD_REQUEST);
        }

        List<UUID> schedule = new ArrayList<>();
        Set<VersionKey> desired = new HashSet<>();
        for (StoryChapter chapter : story.getChapters()) {
            String normalized = StoryTextNormalizer.normalizeStoryText(chapter.getContent()).trim();
            String hash = StoryAudioText.hash(normalized);
            List<String> chunks = StoryAudioText.chunks(normalized, properties.effectiveChunkMaxCharacters());
            for (int index = 0; index < chunks.size(); index++) {
                int chunkIndex = index;
                desired.add(new VersionKey(chapter.getId(), hash, chunkIndex));
                StoryAudio audio = audios.findByChapter_IdAndTextHashAndVoiceAndChunkIndex(
                                chapter.getId(), hash, openAiAudio.voice(), chunkIndex)
                        .orElseGet(() -> audios.save(new StoryAudio(story, chapter, chunkIndex, hash,
                                openAiAudio.voice(), openAiAudio.model(), openAiAudio.format())));
                if (audio.getStatus() == StoryAudioStatus.FAILED
                        && audio.getAttemptCount() < properties.effectiveMaxAttempts()) audio.retry();
                if (audio.getStatus() == StoryAudioStatus.PENDING) schedule.add(audio.getId());
            }
        }
        for (StoryAudio old : new ArrayList<>(audios.findByStory_IdOrderByChapter_ChapterNumberAscChunkIndexAsc(storyId))) {
            VersionKey key = new VersionKey(old.getChapter().getId(), old.getTextHash(), old.getChunkIndex());
            if (!desired.contains(key) || !openAiAudio.voice().equals(old.getVoice())) {
                deletionQueue.enqueue(FileDeletionType.STORY_AUDIO, old.getStorageKey());
                audios.delete(old);
            }
        }
        if (!schedule.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { schedule.forEach(StoryAudioGenerationService.this::processAsync); }
            });
        }
        audios.flush();
        return response(storyId);
    }

    public void processAsync(UUID audioId) {
        if (!properties.enabled() || !active.add(audioId)) return;
        try {
            executor.execute(() -> {
                try { process(audioId, false); }
                finally { active.remove(audioId); }
            });
        } catch (RejectedExecutionException exception) {
            active.remove(audioId);
            LOGGER.warn("story_audio_queue_full audioId={}", audioId);
        }
    }

    void process(UUID audioId) { process(audioId, false); }

    private void process(UUID audioId, boolean recoverStale) {
        Work work = transactions.execute(status -> claim(audioId, recoverStale));
        if (work == null) return;
        String storedKey = null;
        try {
            byte[] bytes = generator.generate(work.text());
            if (!properties.enabled()) throw new AudioDisabledDuringGeneration();
            storedKey = storage.saveStoryAudio(bytes, work.storyId().toString(), work.filename());
            String completedKey = storedKey;
            Boolean completed = transactions.execute(status -> audios.findForUpdate(audioId).map(audio -> {
                if (audio.getStatus() != StoryAudioStatus.PROCESSING || !audio.getStory().isActive() || !properties.enabled()) return false;
                audio.complete(completedKey, bytes.length);
                return true;
            }).orElse(false));
            if (!Boolean.TRUE.equals(completed)) deletionQueue.enqueueStoryAudioAfterRace(storedKey);
        } catch (AudioDisabledDuringGeneration exception) {
            transactions.executeWithoutResult(status -> audios.findForUpdate(audioId).ifPresent(audio -> audio.retry()));
        } catch (Exception exception) {
            if (storedKey != null) deletionQueue.enqueueStoryAudioAfterRace(storedKey);
            transactions.executeWithoutResult(status -> audios.findForUpdate(audioId).ifPresent(audio -> {
                if (audio.getStatus() == StoryAudioStatus.PROCESSING) {
                    audio.fail("Não foi possível gerar este trecho. Tente novamente.");
                }
            }));
            LOGGER.warn("story_audio_generation_failed audioId={} reason={}", audioId, exception.getClass().getSimpleName());
        }
    }

    private Work claim(UUID audioId, boolean recoverStale) {
        if (!properties.enabled()) return null;
        StoryAudio audio = audios.findForUpdate(audioId).orElse(null);
        if (audio == null || !audio.getStory().isActive()) return null;
        boolean pending = audio.getStatus() == StoryAudioStatus.PENDING;
        boolean stale = recoverStale && audio.getStatus() == StoryAudioStatus.PROCESSING
                && audio.getUpdatedAt().isBefore(Instant.now().minus(STALE_AFTER));
        if (!pending && !stale) return null;
        if (audio.getAttemptCount() >= properties.effectiveMaxAttempts()) {
            audio.fail("A narração atingiu o limite de tentativas.");
            return null;
        }
        String normalized = StoryTextNormalizer.normalizeStoryText(audio.getChapter().getContent()).trim();
        String currentHash = StoryAudioText.hash(normalized);
        if (!currentHash.equals(audio.getTextHash()) || !openAiAudio.voice().equals(audio.getVoice())) return null;
        List<String> chunks = StoryAudioText.chunks(normalized, properties.effectiveChunkMaxCharacters());
        if (audio.getChunkIndex() >= chunks.size()) return null;
        audio.start();
        String extension = openAiAudio.format() == null || openAiAudio.format().isBlank() ? "mp3" : openAiAudio.format();
        return new Work(audio.getStory().getId(), chunks.get(audio.getChunkIndex()),
                "audio-" + audio.getId() + "." + extension);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() { recover(); }

    @Scheduled(fixedDelayString = "${app.story.audio.recovery-delay-ms:60000}")
    public void recover() {
        if (!properties.enabled()) return;
        audios.findByStatus(StoryAudioStatus.PENDING).forEach(item -> processAsync(item.getId()));
        audios.findByStatusAndUpdatedAtBefore(StoryAudioStatus.PROCESSING, Instant.now().minus(STALE_AFTER))
                .forEach(item -> processStaleAsync(item.getId()));
    }

    private void processStaleAsync(UUID id) {
        if (!active.add(id)) return;
        try {
            executor.execute(() -> {
                try { process(id, true); }
                finally { active.remove(id); }
            });
        } catch (RejectedExecutionException exception) { active.remove(id); }
    }

    private Story requireStory(UUID id) { return stories.findByIdAndActiveTrue(id).orElseThrow(this::notFound); }
    private BusinessException notFound() { return new BusinessException("STORY_NOT_FOUND", "História não encontrada", HttpStatus.NOT_FOUND); }
    private StoryNarrationResponse response(UUID storyId) {
        List<StoryAudio> items = audios.findByStory_IdOrderByChapter_ChapterNumberAscChunkIndexAsc(storyId).stream()
                .filter(item -> openAiAudio.voice().equals(item.getVoice()))
                .sorted(Comparator.comparingInt((StoryAudio item) -> item.getChapter().getChapterNumber()).thenComparingInt(StoryAudio::getChunkIndex))
                .toList();
        return StoryNarrationResponse.from(storyId, openAiAudio.voice(), items);
    }

    private record VersionKey(UUID chapterId, String hash, int chunkIndex) {}
    private record Work(UUID storyId, String text, String filename) {}
    private static final class AudioDisabledDuringGeneration extends RuntimeException {}
}
