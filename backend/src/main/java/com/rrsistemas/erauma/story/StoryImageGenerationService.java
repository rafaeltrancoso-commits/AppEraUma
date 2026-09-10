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
import java.util.ArrayList;
import java.util.Comparator;
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
    private final StoryVisualStyle visualStyle;
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
            StoryVisualStyle visualStyle) {
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
        this.visualStyle = visualStyle;
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
                                image.updatePlan(plan.chapterStart(), plan.chapterEnd(), sanitizePrompt(plan.prompt()));
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
                                        sanitizePrompt(plan.prompt())));
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

    @Transactional
    public StoryImageResponse retryFailedImage(UUID imageId, AppUser user) {
        StoryImage image = images.findForRetry(imageId)
                .orElseThrow(() -> new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND));
        familyService.requireMembership(image.getStory().getFamilyId(), user);
        if (image.getStatus() != StoryImageStatus.FAILED) {
            throw new BusinessException("STORY_IMAGE_RETRY_NOT_ALLOWED", "Somente imagens com falha podem ser reprocessadas.", HttpStatus.BAD_REQUEST);
        }
        image.updatePlan(image.getChapterStart(), image.getChapterEnd(), image.getPromptText());
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
            story.updateGenerationFromImages(completed);
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
                image.markGenerating();
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
                .filter(image -> image.getStatus() != StoryImageStatus.GENERATED && image.getStatus() != StoryImageStatus.FAILED)
                .map(image -> new ImageWork(image.getId(), image.getStory().getId(), image.getImageType(), image.getSortOrder(), image.getPromptText()))
                .orElse(null));
        if (work == null) return;
        activeImages.add(imageId);
        long startedAt = System.nanoTime();
        try {
            GeneratedStoryImage generated = generator.generate(work.prompt());
            StoryImageIntegrity.Validation received = validateReceivedImage(work.imageId(), generated);
            String storageKey = storage.saveStoryImage(generated.pngBytes(), work.storyId().toString(), filename(work));
            verifyStoredImage(work.imageId(), storageKey, received);
            long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            transactionTemplate.executeWithoutResult(status -> images.findById(imageId).ifPresent(image -> {
                image.markGenerated(storageKey, generated.model(), generated.size(), generated.quality());
                logs.save(new AiImageGenerationLog(image.getStory().getCreatedBy(), image.getStory().getFamily(), image.getStory(), image, provider(generated), generated.model(), generated.quality(), generated.size(), StoryImageStatus.GENERATED, durationMs, costEstimator.estimate(generated.quality()), representedChapters(image), sanitizePrompt(work.prompt()), null));
                LOGGER.info("story_image_generation imageId={} type={} chapters={} provider={} model={} status={} durationMs={}", image.getId(), image.getImageType(), representedChapters(image), provider(generated), generated.model(), StoryImageStatus.GENERATED, durationMs);
            }));
        } catch (IOException exception) {
            transactionTemplate.executeWithoutResult(status -> images.findById(imageId).ifPresent(image -> markFailed(image, "storage", exception, familyId, userId)));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> images.findById(imageId).ifPresent(image -> markFailed(image, "openai", exception, familyId, userId)));
        } finally {
            activeImages.remove(imageId);
        }
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
        markFailed(image, provider, exception.getClass().getSimpleName(), exception.getMessage());
    }

    private void markFailed(StoryImage image, String provider, IOException exception, UUID familyId, UUID userId) {
        markFailed(image, provider, "storage", exception.getMessage());
    }

    private void markFailed(StoryImage image, String provider, String reason, String message) {
        String sanitizedMessage = sanitizeError(message);
        image.markFailed(sanitizedMessage);
        logs.save(new AiImageGenerationLog(image.getStory().getCreatedBy(), image.getStory().getFamily(), image.getStory(), image, provider, openAiImageProperties.model(), openAiImageProperties.quality(), openAiImageProperties.size(), StoryImageStatus.FAILED, null, java.math.BigDecimal.ZERO, representedChapters(image), sanitizePrompt(image.getPromptText()), sanitizedMessage));
        LOGGER.warn("story_image_generation imageId={} type={} chapters={} provider={} model={} status={} reason={}", image.getId(), image.getImageType(), representedChapters(image), provider, openAiImageProperties.model(), StoryImageStatus.FAILED, sanitizeError(reason));
    }

    private List<ImagePlan> plans(Story story) {
        String base = basePrompt(story);
        List<StoryChapter> chapters = story.getChapters().stream().sorted(Comparator.comparingInt(StoryChapter::getChapterNumber)).toList();
        List<ImagePlan> plans = new ArrayList<>();
        plans.add(new ImagePlan(StoryImageType.COVER, null, null, 0, "cover.png", StoryImageFormat.SINGLE_SCENE, base + singleSceneRules() + "\nCENA PRINCIPAL:\nCapa encantadora da historia, mostrando os personagens principais no local central, clima de descoberta e afeto."));
        int sceneImages = Math.min(StoryLengthSpec.of(story.getLength()).sceneImages(), Math.max(0, properties.maxImages() - 1));
        for (int index = 0; index < sceneImages; index++) {
            int chapterStart = index * 2 + 1;
            int chapterEnd = Math.min(chapterStart + 1, chapters.size());
            String sceneText = groupedSceneText(chapters, chapterStart, chapterEnd);
            StoryImageFormat format = story.getLength() == StoryLength.LONG && index == 1 && chapters.size() >= 4
                    ? StoryImageFormat.COMIC_THREE_PANELS : StoryImageFormat.SINGLE_SCENE;
            if (format == StoryImageFormat.COMIC_THREE_PANELS) {
                chapterStart = 2;
                chapterEnd = 4;
            }
            String formatRules = format == StoryImageFormat.COMIC_THREE_PANELS
                    ? comicRules(chapters, chapterStart, chapterEnd) : singleSceneRules();
            plans.add(new ImagePlan(StoryImageType.SCENE, chapterStart, chapterEnd, index + 1, "scene-" + (index + 1) + ".png", format, base + formatRules + "\nCENA PRINCIPAL:\n"
                    + sceneRole(index, sceneImages) + "\n"
                    + "Represente somente este momento especifico da narrativa, correspondente aos blocos internos " + chapterStart + "-" + chapterEnd + ": " + sceneText + "\n"
                    + "Esta imagem precisa ser visualmente diferente das outras cenas da mesma historia: varie acao, pose, expressao, enquadramento e detalhes do ambiente, mantendo a ficha fixa do personagem e a roupa fixa.\n"
                    + "Nao crie uma imagem generica de personagens posando. Nao reutilize composicao de cena anterior.\n"
                    + "Composicao segura para criancas, expressoes acolhedoras, sem texto, letras, legendas, logotipos ou marcas na imagem."));
        }
        return plans;
    }

    private String singleSceneRules() {
        return "\nFORMATO SINGLE_SCENE OBRIGATORIO:\nUma unica cena ocupando toda a imagem; sem colagem, divisao ou quadros adicionais; sem texto, titulo, letras, numeros, simbolos, assinatura ou baloes; nao repita o mesmo personagem de forma incoerente.";
    }

    private String comicRules(List<StoryChapter> chapters, int start, int end) {
        List<StoryChapter> selected = chapters.stream().filter(chapter -> chapter.getChapterNumber() >= start && chapter.getChapterNumber() <= end).limit(3).toList();
        if (selected.size() != 3) return singleSceneRules();
        return "\nFORMATO COMIC_THREE_PANELS OBRIGATORIO:\nExatamente tres quadros: um grande em cima; dois menores lado a lado embaixo. Ordem: superior, inferior esquerdo, inferior direito. Sem quadros extras, baloes, legendas, palavras, letras, numeros, titulo ou assinatura. Nao introduza personagens que nao estejam nas fichas ou na cena. Cada quadro mostra um momento diferente e consecutivo, sem repetir a mesma pessoa dentro do mesmo quadro.\n"
                + "QUADRO 1: " + clean(limit(selected.get(0).getContent(), 260)) + "\n"
                + "QUADRO 2: " + clean(limit(selected.get(1).getContent(), 260)) + "\n"
                + "QUADRO 3: " + clean(limit(selected.get(2).getContent(), 260));
    }

    private String sceneRole(int index, int totalScenes) {
        if (index == 0) {
            return "Momento de abertura: chegada, descoberta inicial ou primeiro sinal da aventura.";
        }
        if (index == totalScenes - 1) {
            return "Momento final: acao de resolucao, consequencias e fechamento acolhedor.";
        }
        return "Momento intermediario: tentativa, plano, obstaculo ou descoberta que muda a direcao da historia.";
    }

    private String basePrompt(Story story) {
        return visualStyle.cartoonPrompt() + "\n\n"
                + "FICHAS VISUAIS CANONICAS:\n" + canonicalCharacters(story) + "\n\n"
                + "ROUPA FIXA:\n" + outfit(story) + "\n\n"
                + "CONSISTENCIA OBRIGATORIA:\nMantenha o mesmo estilo cartoon, rosto, idade aparente, cabelo, olhos, tom de pele, roupa, acessorios, proporcoes, paleta de cores, nivel de detalhamento, iluminacao e acabamento em todas as ilustracoes desta historia. A consistencia e orientada por texto, sem referencia visual ou seed.\n\n"
                + "PERSONAGENS SECUNDARIOS:\n" + secondCharacter(story) + "\n\n"
                + "AMBIENTE:\n" + clean(firstNonBlank(story.getPlace(), "ambiente infantil acolhedor")) + "\n\n"
                + "TEMA:\n" + clean(story.getTheme());
    }

    private String canonicalCharacters(Story story) {
        if (story.getCharacters() == null || story.getCharacters().isEmpty()) return CharacterVisualProfile.from(story).toPromptText();
        return story.getCharacters().stream()
                .map(item -> "POSICAO " + item.getSelectionOrder() + " (" + item.getRole() + "): " + item.getVisualDescription())
                .reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private String outfit(Story story) {
        return "roupas praticas e confortaveis em " + colorFor(story.getId()) + ", adequadas a idade de cada personagem e consistentes em todas as imagens";
    }

    private String colorFor(UUID storyId) {
        String[] colors = {"azul claro", "verde folha", "amarelo suave", "vermelho coral", "lilas suave", "turquesa"};
        int index = Math.floorMod(storyId.hashCode(), colors.length);
        return colors[index];
    }

    private String secondCharacter(Story story) {
        if (story.getSecondCharacterName() == null || story.getSecondCharacterName().isBlank()) {
            return "Sem personagem secundario fixo informado.";
        }
        return clean(story.getSecondCharacterName()) + ": personagem secundario com aparencia generica segura e idade nao presumida, roupas simples e consistentes; nao inferir etnia.";
    }

    private String groupedSceneText(List<StoryChapter> chapters, int chapterStart, int chapterEnd) {
        return chapters.stream()
                .filter(chapter -> chapter.getChapterNumber() >= chapterStart && chapter.getChapterNumber() <= chapterEnd)
                .map(chapter -> clean(chapter.getTitle()) + ": " + clean(limit(chapter.getContent(), 320)))
                .reduce((left, right) -> left + " " + right)
                .map(value -> limit(value, 760))
                .orElse("Momento visual da historia mostrando continuidade, descoberta e afeto.");
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
        return image.type() == StoryImageType.COVER ? "cover.png" : "scene-" + image.sortOrder() + ".png";
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
        if (image.getChapterStart() == null || image.getChapterEnd() == null) {
            return "";
        }
        return image.getChapterStart().equals(image.getChapterEnd()) ? image.getChapterStart().toString() : image.getChapterStart() + "-" + image.getChapterEnd();
    }

    private String sanitizePrompt(String value) {
        String clean = clean(value);
        return clean.length() > 3500 ? clean.substring(0, 3500) : clean;
    }

    private String sanitizeError(String value) {
        String clean = clean(value)
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [redacted]")
                .replaceAll("sk-[A-Za-z0-9_\\-]+", "[redacted]");
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }

    private String clean(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim(); }
    private String firstNonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String limit(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
    private String provider(GeneratedStoryImage generated) { return generated.model() != null && generated.model().startsWith("mock") ? "mock" : "openai"; }
    private record ImagePlan(StoryImageType type, Integer chapterStart, Integer chapterEnd, int sortOrder, String filename, StoryImageFormat format, String prompt) {}
    private record ImageWork(UUID imageId, UUID storyId, StoryImageType type, int sortOrder, String prompt) {}
}
