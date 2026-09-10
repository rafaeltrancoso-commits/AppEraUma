package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.notification.PushNotificationService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StoryGenerationProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryGenerationProcessor.class);
    private static final java.time.Duration STALE_WORK_AFTER = java.time.Duration.ofMinutes(5);
    private final StoryRepository stories;
    private final StoryGenerator generator;
    private final AiGenerationLogRepository logs;
    private final StoryAiProperties properties;
    private final StoryImageGenerationService imageService;
    private final Executor executor;
    private final TransactionTemplate transactions;
    private final PushNotificationService notifications;
    private final java.util.Set<UUID> activeStories = ConcurrentHashMap.newKeySet();

    public StoryGenerationProcessor(StoryRepository stories, StoryGenerator generator,
            AiGenerationLogRepository logs, StoryAiProperties properties,
            StoryImageGenerationService imageService, PlatformTransactionManager transactionManager,
            @Qualifier("storyGenerationExecutor") Executor executor, PushNotificationService notifications) {
        this.stories = stories;
        this.generator = generator;
        this.logs = logs;
        this.properties = properties;
        this.imageService = imageService;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.notifications = notifications;
    }

    public void processAsync(UUID storyId) {
        try {
            executor.execute(() -> process(storyId, false));
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("story_generation_queue_full storyId={}", storyId);
        }
    }

    void process(UUID storyId) { process(storyId, false); }

    private void process(UUID storyId, boolean recoverStale) {
        Work work = transactions.execute(status -> claim(storyId, recoverStale));
        if (work == null) return;
        activeStories.add(storyId);
        long startedAt = System.nanoTime();
        try {
            GeneratedStory generated = generator.generate(work.request());
            Boolean illustrated = transactions.execute(status -> {
                Story story = stories.findByIdAndActiveTrue(storyId).orElse(null);
                if (story == null || story.getGenerationStatus() != StoryGenerationStatus.PROCESSANDO_TEXTO) return false;
                story.completeText(generated);
                logs.save(new AiGenerationLog(story.getCreatedBy(), story.getFamily(), story, generated,
                        "mock-fallback".equals(generated.provider()) ? AiGenerationStatus.FALLBACK : AiGenerationStatus.SUCCESS));
                boolean generateImages = story.getGenerationMode() == StoryGenerationMode.ILLUSTRATED
                        && imageService.isGenerationEnabled();
                if (generateImages) imageService.createInitialImageRecords(story);
                else if (story.getGenerationMode() == StoryGenerationMode.ILLUSTRATED) story.updateGenerationFromImages(StoryGenerationStatus.CONCLUIDA);
                return generateImages;
            });
            if (Boolean.TRUE.equals(illustrated)) imageService.processStoryImagesAsync(storyId, work.familyId(), work.userId());
            else notifySafely(work.userId(), storyId, StoryGenerationStatus.CONCLUIDA);
        } catch (RuntimeException exception) {
            long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            transactions.executeWithoutResult(status -> stories.findByIdAndActiveTrue(storyId).ifPresent(story -> {
                if (story.getGenerationStatus() != StoryGenerationStatus.PROCESSANDO_TEXTO) return;
                story.markGenerationFailed("Não conseguimos criar sua história agora. Tente novamente.");
                logs.save(new AiGenerationLog(story.getCreatedBy(), story.getFamily(), story, properties.generator(), null, AiGenerationStatus.FAILED, durationMs));
            }));
            LOGGER.warn("async_story_generation_failed storyId={} reason={}", storyId, exception.getClass().getSimpleName());
        } finally {
            activeStories.remove(storyId);
        }
    }

    private Work claim(UUID storyId, boolean recoverStale) {
        Story story = stories.findForGeneration(storyId).orElse(null);
        if (story == null) return null;
        boolean pending = story.getGenerationStatus() == StoryGenerationStatus.PENDENTE;
        boolean stale = recoverStale
                && story.getGenerationStatus() == StoryGenerationStatus.PROCESSANDO_TEXTO
                && story.getUpdatedAt().isBefore(java.time.Instant.now().minus(STALE_WORK_AFTER));
        if (!pending && !stale) return null;
        if (story.getGenerationAttemptCount() >= properties.effectiveMaxAttempts()) {
            story.markGenerationFailed("Não conseguimos criar sua história após algumas tentativas.");
            return null;
        }
        story.markProcessingText();
        List<StoryCharacterPrompt> characters = story.getCharacters().stream().map(item -> {
            var profile = item.getCharacter();
            return new StoryCharacterPrompt(profile.getId(), profile.getName(), profile.getNickname(), profile.getBirthDate(),
                    item.getSelectionOrder(), item.getRole(), item.getVisualDescription());
        }).toList();
        var protagonist = characters.isEmpty() ? null : characters.get(0);
        var readingBirthDate = characters.stream().map(StoryCharacterPrompt::birthDate).filter(java.util.Objects::nonNull)
                .filter(date -> java.time.Period.between(date, java.time.LocalDate.now()).getYears() < 18).findFirst().orElse(null);
        return new Work(story.getFamilyId(), story.getCreatedBy().getId(), new StoryGenerationRequest(
                story.getFamilyId(), protagonist == null ? null : protagonist.id(), protagonist == null ? story.getMainCharacterName() : protagonist.name(),
                readingBirthDate, story.getMainCharacterName(), story.getSecondCharacterName(),
                story.getSourceMoment() == null ? null : story.getSourceMoment().getId(),
                story.getSourceMoment() == null ? null : story.getSourceMoment().getTitle(),
                story.getSourceMoment() == null ? null : story.getSourceMoment().getDescription(),
                story.getSourceMoment() == null ? null : story.getSourceMoment().getLocationName(), story.getTheme(), story.getPlace(),
                story.getFavoriteAnimal(), story.getStyle(), story.getLength(), characters, story.getOtherCharacters()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        stories.findByGenerationStatusInAndActiveTrue(List.of(StoryGenerationStatus.PENDENTE))
                .forEach(story -> processAsync(story.getId()));
        recoverStaleTextWork();
        stories.findByGenerationStatusInAndActiveTrue(List.of(StoryGenerationStatus.PROCESSANDO_IMAGENS))
                .forEach(story -> {
                    transactions.executeWithoutResult(status -> {
                    Story current = stories.findForGeneration(story.getId()).orElse(null);
                    if (current != null && imageService.isGenerationEnabled()) imageService.createInitialImageRecords(current);
                    else if (current != null) current.updateGenerationFromImages(StoryGenerationStatus.CONCLUIDA);
                    });
                    imageService.processStoryImagesAsync(story.getId(), story.getFamilyId(), story.getCreatedBy().getId());
                });
    }

    @Scheduled(fixedDelayString = "${erauma.story.recovery-delay-ms:60000}")
    public void recoverQueuedWork() {
        heartbeatActiveWork();
        stories.findByGenerationStatusInAndActiveTrue(List.of(StoryGenerationStatus.PENDENTE))
                .forEach(story -> processAsync(story.getId()));
        recoverStaleTextWork();
    }

    private void recoverStaleTextWork() {
        java.time.Instant staleBefore = java.time.Instant.now().minus(STALE_WORK_AFTER);
        stories.findByGenerationStatusAndUpdatedAtBeforeAndActiveTrue(StoryGenerationStatus.PROCESSANDO_TEXTO, staleBefore)
                .forEach(story -> processStaleAsync(story.getId()));
    }

    private void processStaleAsync(UUID storyId) {
        try {
            executor.execute(() -> process(storyId, true));
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("story_generation_recovery_queue_full storyId={}", storyId);
        }
    }

    private void heartbeatActiveWork() {
        java.time.Instant now = java.time.Instant.now();
        activeStories.forEach(storyId -> transactions.executeWithoutResult(status -> stories.heartbeatGeneration(storyId, now)));
    }

    private void notifySafely(UUID userId, UUID storyId, StoryGenerationStatus status) {
        try {
            notifications.storyReady(userId, storyId, status);
        } catch (RuntimeException exception) {
            LOGGER.warn("story_ready_notification_failed storyId={} reason={}", storyId, exception.getClass().getSimpleName());
        }
    }

    private record Work(UUID familyId, UUID userId, StoryGenerationRequest request) {}
}
