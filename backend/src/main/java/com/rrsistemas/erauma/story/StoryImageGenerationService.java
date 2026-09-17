package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.notification.PushNotificationService;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.moment.StoredFile;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class StoryImageGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryImageGenerationService.class);
    private static final java.time.Duration STALE_IMAGE_AFTER = java.time.Duration.ofMinutes(5);
    private final StoryImageGenerator generator;
    private final StoryRepository stories;
    private final StoryImageRepository images;
    private final FamilyService familyService;
    private final AiImageGenerationLogRepository logs;
    private final FileStorageService storage;
    private final StoryImageProperties properties;
    private final OpenAiImageProperties openAiImageProperties;
    private final ImageCostEstimator costEstimator;
    private final Executor storyImageExecutor;
    private final TransactionTemplate transactionTemplate;
    private final PushNotificationService notifications;
    private final StoryImagePromptBuilder promptBuilder;
    private final java.util.Set<UUID> activeImages = ConcurrentHashMap.newKeySet();

    public StoryImageGenerationService(
            StoryImageGenerator generator,
            StoryRepository stories,
            StoryImageRepository images,
            FamilyService familyService,
            AiImageGenerationLogRepository logs,
            FileStorageService storage,
            StoryImageProperties properties,
            OpenAiImageProperties openAiImageProperties,
            ImageCostEstimator costEstimator,
            PlatformTransactionManager transactionManager,
            @Qualifier("storyImageExecutor") Executor storyImageExecutor,
            PushNotificationService notifications,
            StoryImagePromptBuilder promptBuilder) {
        this.generator = generator;
        this.stories = stories;
        this.images = images;
        this.familyService = familyService;
        this.logs = logs;
        this.storage = storage;
        this.properties = properties;
        this.openAiImageProperties = openAiImageProperties;
        this.costEstimator = costEstimator;
        this.storyImageExecutor = storyImageExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.notifications = notifications;
        this.promptBuilder = promptBuilder;
    }

    @Transactional
    public void createInitialImageRecords(Story story) {
        if (!properties.generationEnabled()) {
            return;
        }
        for (ImagePlan plan : plans(story)) {
            images.findByStory_IdAndImageTypeAndSortOrder(story.getId(), plan.type(), plan.sortOrder())
                    .ifPresentOrElse(
                            image -> {
                                image.updatePlan(plan.chapterStart(), plan.chapterEnd());
                                image.setVisualFormat(plan.format());
                            },
                            () -> {
                                StoryChapter chapter = firstChapter(story, plan.chapterStart());
                                StoryImage image = images.save(new StoryImage(
                                        story,
                                        chapter,
                                        plan.type(),
                                        openAiImageProperties.model(),
                                        openAiImageProperties.size(),
                                        openAiImageProperties.quality(),
                                        plan.sortOrder(),
                                        plan.chapterStart(),
                                        plan.chapterEnd(),
                                        null));
                                image.setVisualFormat(plan.format());
                                story.getImages().add(image);
                            });
        }
    }

    public void processStoryImagesAsync(UUID storyId, UUID familyId, UUID userId) {
        processStoryImagesAsync(storyId, familyId, userId, false);
    }

    private void processStoryImagesAsync(UUID storyId, UUID familyId, UUID userId, boolean recoverStale) {
        if (!properties.generationEnabled()) {
            return;
        }
        try {
            storyImageExecutor.execute(() -> processPendingStoryImages(storyId, familyId, userId, recoverStale));
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("story_image_queue_full storyId={}", storyId);
        }
    }

    public boolean isGenerationEnabled() { return properties.generationEnabled(); }

    @Transactional(readOnly = true)
    public void recoverPendingImages() {
        if (!properties.generationEnabled()) {
            return;
        }
        List<UUID> storyIds = images.findDistinctStoryIdsByStatusIn(List.of(StoryImageStatus.PENDING));
        for (UUID storyId : storyIds) {
            stories.findByIdAndActiveTrue(storyId)
                    .ifPresent(story -> processStoryImagesAsync(story.getId(), story.getFamily().getId(), story.getCreatedBy().getId()));
        }
        recoverStaleGeneratingImages();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingImagesOnStartup() {
        recoverPendingImages();
    }

    @Scheduled(fixedDelayString = "${erauma.story.image-recovery-delay-ms:60000}")
    public void recoverQueuedImages() {
        if (!properties.generationEnabled()) return;
        heartbeatActiveImages();
        images.findDistinctStoryIdsByStatusIn(List.of(StoryImageStatus.PENDING)).forEach(storyId ->
                stories.findByIdAndActiveTrue(storyId).ifPresent(story ->
                        processStoryImagesAsync(storyId, story.getFamilyId(), story.getCreatedBy().getId())));
        recoverStaleGeneratingImages();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reconcileMissingFile(UUID imageId, String expectedStorageKey) {
        images.findForRetry(imageId).ifPresent(image -> {
            if (image.getStatus() != StoryImageStatus.GENERATED || !java.util.Objects.equals(image.getStorageKey(), expectedStorageKey)) {
                return;
            }
            image.markFailed("Arquivo da imagem nao encontrado no storage.");
            logs.save(new AiImageGenerationLog(image.getStory().getCreatedBy(), image.getStory().getFamily(), image.getStory(), image, "storage", openAiImageProperties.model(), openAiImageProperties.quality(), openAiImageProperties.size(), StoryImageStatus.FAILED, null, java.math.BigDecimal.ZERO, representedChapters(image), null, AiImageFailureReason.STORAGE_FAILURE.name()));
            LOGGER.warn("story_image_reconciled imageId={} type={} chapters={} previousStatus=GENERATED newStatus=FAILED reason=file_missing", image.getId(), image.getImageType(), representedChapters(image));
            Story story = image.getStory();
            if (story.getGenerationStatus() == StoryGenerationStatus.CONCLUIDA) {
                story.updateGenerationFromImages(StoryGenerationStatus.CONCLUIDA_COM_FALHAS);
            }
        });
    }

    @Transactional
    public StoryImageResponse retryFailedImage(UUID imageId, AppUser user) {
        StoryImage image = images.findForRetry(imageId)
                .orElseThrow(() -> new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND));
        familyService.requireMembership(image.getStory().getFamilyId(), user);
        if (image.getStatus() != StoryImageStatus.FAILED) {
            throw new BusinessException("STORY_IMAGE_RETRY_NOT_ALLOWED", "Somente imagens com falha podem ser reprocessadas.", HttpStatus.BAD_REQUEST);
        }
        if (image.getAttemptCount() >= properties.effectiveMaxAttempts()) {
            throw new BusinessException("STORY_IMAGE_ATTEMPT_LIMIT_REACHED", "Esta imagem atingiu o limite de tentativas.", HttpStatus.TOO_MANY_REQUESTS);
        }
        image.queueForGeneration();
        images.save(image);
        UUID storyId = image.getStory().getId();
        UUID familyId = image.getStory().getFamilyId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { processStoryImagesAsync(storyId, familyId, user.getId()); }
        });
        return StoryImageResponse.from(image);
    }

    private void processPendingStoryImages(UUID storyId, UUID familyId, UUID userId, boolean recoverStale) {
        while (true) {
            List<UUID> pending = claimPendingImages(storyId, recoverStale);
            if (pending.isEmpty()) break;
            processOneImage(pending.get(0), familyId, userId);
        }
        StoryGenerationStatus completedStatus = updateStoryGenerationStatus(storyId);
        if (completedStatus != null) notifySafely(userId, storyId, completedStatus);
    }

    private StoryGenerationStatus updateStoryGenerationStatus(UUID storyId) {
        return transactionTemplate.execute(status -> stories.findByIdAndActiveTrue(storyId).map(story -> {
            List<StoryImage> planned = images.findByStory_IdOrderBySortOrderAsc(storyId);
            boolean anyFailed = planned.stream().anyMatch(image -> image.getStatus() == StoryImageStatus.FAILED);
            boolean anyPending = planned.stream().anyMatch(image -> image.getStatus() == StoryImageStatus.PENDING || image.getStatus() == StoryImageStatus.GENERATING);
            if (anyPending) return null;
            StoryGenerationStatus completed = anyFailed ? StoryGenerationStatus.CONCLUIDA_COM_FALHAS : StoryGenerationStatus.CONCLUIDA;
            StoryGenerationStatus previous = story.getGenerationStatus();
            story.updateGenerationFromImages(completed);
            LOGGER.info("story_generation_transition storyId={} previousStatus={} newStatus={} imageCount={} failedImages={}",
                    storyId, previous, completed, planned.size(), planned.stream().filter(image -> image.getStatus() == StoryImageStatus.FAILED).count());
            return completed;
        }).orElse(null));
    }

    List<UUID> claimPendingImages(UUID storyId) {
        return claimPendingImages(storyId, false);
    }

    private List<UUID> claimPendingImages(UUID storyId, boolean recoverStale) {
        return transactionTemplate.execute(status -> {
            java.time.Instant staleBefore = java.time.Instant.now().minus(STALE_IMAGE_AFTER);
            List<StoryImage> candidates = images.findClaimable(storyId, recoverStale, staleBefore);
            for (StoryImage image : candidates) {
                if (image.getAttemptCount() >= properties.effectiveMaxAttempts()) {
                    image.markFailed("A imagem atingiu o limite de tentativas.");
                    continue;
                }
                StoryImageStatus previous = image.getStatus();
                image.markGenerating();
                LOGGER.info("story_image_transition storyId={} imageId={} type={} previousStatus={} newStatus={} attempt={}",
                        storyId, image.getId(), image.getImageType(), previous, StoryImageStatus.GENERATING, image.getAttemptCount());
                return List.of(image.getId());
            }
            return List.of();
        });
    }

    private void recoverStaleGeneratingImages() {
        java.time.Instant staleBefore = java.time.Instant.now().minus(STALE_IMAGE_AFTER);
        images.findDistinctStaleGeneratingStoryIds(staleBefore).forEach(storyId ->
                stories.findByIdAndActiveTrue(storyId).ifPresent(story ->
                        processStoryImagesAsync(storyId, story.getFamilyId(), story.getCreatedBy().getId(), true)));
    }

    void processOneImage(UUID imageId, UUID familyId, UUID userId) {
        ImageWork work = transactionTemplate.execute(status -> images.findById(imageId)
                .filter(image -> image.getStatus() == StoryImageStatus.GENERATING)
                .map(image -> new ImageWork(image.getId(), image.getStory().getId(), UUID.randomUUID(), image.getImageType(), image.getSortOrder(),
                        promptFor(image.getStory(), image), image.getChapterStart(), image.getChapterEnd()))
                .orElse(null));
        if (work == null) return;
        activeImages.add(imageId);
        long startedAt = System.nanoTime();
        try {
            GenerationOutcome outcome = generateWithModerationFallback(work);
            GeneratedStoryImage generated = outcome.image();
            StoryImageIntegrity.Validation received = validateReceivedImage(work.imageId(), generated);
            String storageKey = storage.saveStoryImage(generated.pngBytes(), work.storyId().toString(), filename(work));
            verifyStoredImage(work.imageId(), storageKey, received);
            long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            transactionTemplate.executeWithoutResult(status -> images.findById(imageId).ifPresent(image -> {
                if (image.getStatus() != StoryImageStatus.GENERATING) {
                    LOGGER.info("story_image_result_ignored storyId={} imageId={} currentStatus={} reason=stale_worker", work.storyId(), imageId, image.getStatus());
                    return;
                }
                image.markGenerated(storageKey, generated.model(), generated.size(), generated.quality());
                logs.save(new AiImageGenerationLog(image.getStory().getCreatedBy(), image.getStory().getFamily(), image.getStory(), image, provider(generated), generated.model(), generated.quality(), generated.size(), StoryImageStatus.GENERATED, durationMs, costEstimator.estimate(generated.quality()), representedChapters(image), null, null));
                StorySceneSpecification spec = outcome.promptPlan().specification();
                LOGGER.info("story_image_generation storyId={} imageId={} type={} chapters={} provider={} model={} previousStatus={} newStatus={} durationMs={} safePrompt={} attempt={} promptStrategy={} promptHash={} characterCount={} hasCentralObject={} knownReferenceAdapted={}",
                        work.storyId(), image.getId(), image.getImageType(), representedChapters(image), provider(generated), generated.model(), StoryImageStatus.GENERATING,
                        StoryImageStatus.GENERATED, durationMs, outcome.safePromptUsed(), outcome.attempt(), outcome.promptPlan().strategyVersion(),
                        outcome.promptPlan().promptHash(), spec.maximumCharacterCount(), !spec.centralObject().isBlank(), spec.knownReferenceAdapted());
            }));
        } catch (IOException exception) {
            transactionTemplate.executeWithoutResult(status -> images.findById(imageId).ifPresent(image -> markFailed(image, "storage", exception, familyId, userId)));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> images.findById(imageId).ifPresent(image -> markFailed(image, "openai", exception, familyId, userId)));
        } finally {
            activeImages.remove(imageId);
        }
    }

    private GenerationOutcome generateWithModerationFallback(ImageWork work) {
        try {
            return new GenerationOutcome(generator.generate(work.promptPlan().text()), false, 1, work.promptPlan());
        } catch (AiImageGenerationException exception) {
            if (exception.reason() != AiImageFailureReason.MODERATION_BLOCKED) {
                throw exception;
            }
            LOGGER.warn("story_image_moderation_blocked storyId={} imageId={} type={} chapters={} provider=openai model={} attempt=1 code={} requestId={} safePrompt=false promptStrategy={} promptHash={}",
                    work.storyId(), work.imageId(), work.type(), representedChapters(work.chapterStart(), work.chapterEnd()), openAiImageProperties.model(),
                    sanitizeError(exception.providerErrorCode()), sanitizeError(exception.providerRequestId()), work.promptPlan().strategyVersion(), work.promptPlan().promptHash());
            StoryImagePromptBuilder.PromptPlan safePrompt = buildSafeFallbackPrompt(work);
            if (safePrompt == null) {
                throw exception;
            }
            try {
                return new GenerationOutcome(generator.generate(safePrompt.text()), true, 2, safePrompt);
            } catch (AiImageGenerationException secondException) {
                LOGGER.warn("story_image_safe_retry_failed storyId={} imageId={} type={} chapters={} provider=openai model={} attempt=2 reason={} code={} requestId={} safePrompt=true promptStrategy={} promptHash={}",
                        work.storyId(), work.imageId(), work.type(), representedChapters(work.chapterStart(), work.chapterEnd()), openAiImageProperties.model(),
                        secondException.reason(), sanitizeError(secondException.providerErrorCode()), sanitizeError(secondException.providerRequestId()),
                        safePrompt.strategyVersion(), safePrompt.promptHash());
                throw secondException;
            }
        }
    }

    private StoryImagePromptBuilder.PromptPlan buildSafeFallbackPrompt(ImageWork work) {
        return transactionTemplate.execute(status -> stories.findByIdAndActiveTrue(work.storyId())
                .map(story -> promptBuilder.safePrompt(story, work.type(), work.chapterStart(), work.chapterEnd(), work.sortOrder()))
                .orElse(null));
    }

    private void heartbeatActiveImages() {
        java.time.Instant now = java.time.Instant.now();
        activeImages.forEach(imageId -> transactionTemplate.executeWithoutResult(status -> images.heartbeatGeneration(imageId, now)));
    }

    private void notifySafely(UUID userId, UUID storyId, StoryGenerationStatus status) {
        try {
            notifications.storyReady(userId, storyId, status);
        } catch (RuntimeException exception) {
            LOGGER.warn("story_image_push_failed storyId={} reason={}", storyId, exception.getClass().getSimpleName());
        }
    }

    private void markFailed(StoryImage image, String provider, RuntimeException exception, UUID familyId, UUID userId) {
        if (exception instanceof AiImageGenerationException typed) {
            markFailed(image, provider, typed.reason().name(), typed.getMessage(), typed.providerErrorCode(), typed.providerRequestId());
        } else {
            markFailed(image, provider, exception.getClass().getSimpleName(), exception.getMessage(), null, null);
        }
    }

    private void markFailed(StoryImage image, String provider, IOException exception, UUID familyId, UUID userId) {
        markFailed(image, provider, AiImageFailureReason.STORAGE_FAILURE.name(), exception.getMessage(), null, null);
    }

    private void markFailed(StoryImage image, String provider, String reason, String message, String providerErrorCode, String providerRequestId) {
        if (image.getStatus() != StoryImageStatus.GENERATING) {
            LOGGER.info("story_image_failure_ignored storyId={} imageId={} currentStatus={} reason=stale_worker",
                    image.getStory().getId(), image.getId(), image.getStatus());
            return;
        }
        image.markFailed("Nao foi possivel gerar esta ilustracao. Tente novamente.");
        logs.save(new AiImageGenerationLog(image.getStory().getCreatedBy(), image.getStory().getFamily(), image.getStory(), image, provider, openAiImageProperties.model(), openAiImageProperties.quality(), openAiImageProperties.size(), StoryImageStatus.FAILED, null, java.math.BigDecimal.ZERO, representedChapters(image), null, sanitizeError(reason)));
        LOGGER.warn("story_image_generation storyId={} imageId={} type={} chapters={} provider={} model={} previousStatus={} newStatus={} reason={} code={} requestId={}",
                image.getStory().getId(), image.getId(), image.getImageType(), representedChapters(image), provider,
                openAiImageProperties.model(), StoryImageStatus.GENERATING, StoryImageStatus.FAILED,
                sanitizeError(reason), sanitizeError(providerErrorCode), sanitizeError(providerRequestId));
    }

    private List<ImagePlan> plans(Story story) {
        List<ImagePlan> plans = new java.util.ArrayList<>();
        plans.add(new ImagePlan(StoryImageType.COVER, null, null, 0, StoryImageFormat.SINGLE_SCENE));
        int sceneImages = Math.min(StoryLengthSpec.of(story.getLength()).sceneImages(), Math.max(0, properties.maxImages() - 1));
        for (int index = 0; index < sceneImages; index++) {
            int chapterStart = index * 2 + 1;
            int chapterEnd = Math.min(chapterStart + 1, story.getChapters().size());
            plans.add(new ImagePlan(StoryImageType.SCENE, chapterStart, chapterEnd, index + 1, StoryImageFormat.SINGLE_SCENE));
        }
        return plans;
    }

    private StoryImagePromptBuilder.PromptPlan promptFor(Story story, StoryImage image) {
        return promptBuilder.normalPrompt(story, image);
    }

    private StoryChapter firstChapter(Story story, Integer chapterStart) {
        if (chapterStart == null) {
            return null;
        }
        return story.getChapters().stream()
                .filter(chapter -> chapter.getChapterNumber() == chapterStart)
                .findFirst()
                .orElse(null);
    }

    private String filename(ImageWork image) {
        String prefix = image.type() == StoryImageType.COVER ? "cover" : "scene-" + image.sortOrder();
        return prefix + "-" + image.executionId() + ".png";
    }

    private StoryImageIntegrity.Validation validateReceivedImage(UUID imageId, GeneratedStoryImage generated) throws IOException {
        byte[] bytes = generated == null ? null : generated.pngBytes();
        int receivedBytes = bytes == null ? 0 : bytes.length;
        LOGGER.info("story_image_received imageId={} receivedBytes={}", imageId, receivedBytes);
        StoryImageIntegrity.Validation validation = StoryImageIntegrity.validatePng(bytes);
        LOGGER.info("story_image_integrity imageId={} phase={} validPng={} shaMatch={} contentType={} width={} height={} bytes={}",
                imageId, "received", validation.valid(), true, validation.contentType(), validation.width(), validation.height(), validation.bytes());
        if (!validation.valid()) {
            throw new IOException("Invalid generated story image PNG: " + validation.reason());
        }
        return validation;
    }

    private void verifyStoredImage(UUID imageId, String storageKey, StoryImageIntegrity.Validation expected) throws IOException {
        StoredFile stored = storage.loadStoryImage(storageKey, expected.bytes());
        byte[] storedBytes;
        try (InputStream input = stored.resource().getInputStream()) {
            storedBytes = input.readAllBytes();
        }
        StoryImageIntegrity.Validation validation = StoryImageIntegrity.validatePng(storedBytes);
        boolean shaMatch = expected.sha256().equals(validation.sha256());
        LOGGER.info("story_image_stored imageId={} storageKey={} expectedBytes={} storedBytes={} contentType={}", imageId, storageKey, expected.bytes(), storedBytes.length, stored.contentType());
        LOGGER.info("story_image_integrity imageId={} phase={} validPng={} shaMatch={} contentType={} width={} height={} storageKey={}", imageId, "stored", validation.valid(), shaMatch, validation.contentType(), validation.width(), validation.height(), storageKey);
        if (storedBytes.length != expected.bytes() || !shaMatch || !validation.valid()) {
            throw new IOException("Invalid stored story image PNG: " + validation.reason());
        }
    }

    private String representedChapters(StoryImage image) {
        return representedChapters(image.getChapterStart(), image.getChapterEnd());
    }

    private String representedChapters(Integer chapterStart, Integer chapterEnd) {
        if (chapterStart == null || chapterEnd == null) {
            return "";
        }
        return chapterStart.equals(chapterEnd) ? chapterStart.toString() : chapterStart + "-" + chapterEnd;
    }

    private String sanitizeError(String value) {
        String clean = clean(value)
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [redacted]")
                .replaceAll("sk-[A-Za-z0-9_\\-]+", "[redacted]");
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }

    private String clean(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim(); }
    private String provider(GeneratedStoryImage generated) { return generated.model() != null && generated.model().startsWith("mock") ? "mock" : "openai"; }
    private record ImagePlan(StoryImageType type, Integer chapterStart, Integer chapterEnd, int sortOrder, StoryImageFormat format) {}
    private record ImageWork(UUID imageId, UUID storyId, UUID executionId, StoryImageType type, int sortOrder, StoryImagePromptBuilder.PromptPlan promptPlan, Integer chapterStart, Integer chapterEnd) {}
    private record GenerationOutcome(GeneratedStoryImage image, boolean safePromptUsed, int attempt, StoryImagePromptBuilder.PromptPlan promptPlan) {}
}
