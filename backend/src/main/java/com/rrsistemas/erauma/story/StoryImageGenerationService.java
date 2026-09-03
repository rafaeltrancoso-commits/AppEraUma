package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.family.Family;
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

@Service
public class StoryImageGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryImageGenerationService.class);
    private static final String VISUAL_STYLE = "Ilustracao de livro infantil, acolhedora, colorida e suave, sem aparencia fotografica.";
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
            @Qualifier("storyImageExecutor") Executor storyImageExecutor) {
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
    }

    @Transactional
    public void createInitialImageRecords(Story story) {
        if (!properties.generationEnabled()) {
            return;
        }
        for (ImagePlan plan : plans(story)) {
            images.findByStory_IdAndImageTypeAndSortOrder(story.getId(), plan.type(), plan.sortOrder())
                    .ifPresentOrElse(
                            image -> image.updatePlan(plan.chapterStart(), plan.chapterEnd(), sanitizePrompt(plan.prompt())),
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
                                story.getImages().add(image);
                            });
        }
    }

    public void processStoryImagesAsync(UUID storyId, UUID familyId, UUID userId) {
        if (!properties.generationEnabled()) {
            return;
        }
        storyImageExecutor.execute(() -> processPendingStoryImages(storyId, familyId, userId));
    }

    @Transactional(readOnly = true)
    public void recoverPendingImages() {
        if (!properties.generationEnabled()) {
            return;
        }
        List<UUID> storyIds = images.findDistinctStoryIdsByStatusIn(List.of(StoryImageStatus.PENDING, StoryImageStatus.GENERATING));
        for (UUID storyId : storyIds) {
            stories.findWithChildAndSourceMomentAndChaptersAndImagesByIdAndActiveTrue(storyId)
                    .ifPresent(story -> processStoryImagesAsync(story.getId(), story.getFamily().getId(), story.getCreatedBy().getId()));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingImagesOnStartup() {
        recoverPendingImages();
    }

    @Transactional
    public StoryImageResponse retryFailedImage(UUID imageId, AppUser user) {
        StoryImage image = images.findByIdAndStory_ActiveTrue(imageId)
                .orElseThrow(() -> new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND));
        familyService.requireMembership(image.getStory().getFamilyId(), user);
        if (image.getStatus() != StoryImageStatus.FAILED) {
            throw new BusinessException("STORY_IMAGE_RETRY_NOT_ALLOWED", "Somente imagens com falha podem ser reprocessadas.", HttpStatus.BAD_REQUEST);
        }
        image.markFailed(null);
        image.updatePlan(image.getChapterStart(), image.getChapterEnd(), image.getPromptText());
        image.markGenerating();
        images.save(image);
        processStoryImagesAsync(image.getStory().getId(), image.getStory().getFamilyId(), user.getId());
        return StoryImageResponse.from(image);
    }

    private void processPendingStoryImages(UUID storyId, UUID familyId, UUID userId) {
        List<UUID> pending = claimPendingImages(storyId);
        for (UUID imageId : pending) {
            processOneImage(imageId, familyId, userId);
        }
    }

    List<UUID> claimPendingImages(UUID storyId) {
        return transactionTemplate.execute(status -> {
            List<StoryImage> candidates = images.findByStory_IdAndStatusInOrderBySortOrderAsc(storyId, List.of(StoryImageStatus.PENDING, StoryImageStatus.GENERATING));
            List<UUID> claimed = new ArrayList<>();
            for (StoryImage image : candidates) {
                if (image.getStatus() == StoryImageStatus.PENDING) {
                    image.markGenerating();
                }
                claimed.add(image.getId());
            }
            return claimed;
        });
    }

    void processOneImage(UUID imageId, UUID familyId, UUID userId) {
        transactionTemplate.executeWithoutResult(status -> processOneImageInTransaction(imageId, familyId, userId));
    }

    private void processOneImageInTransaction(UUID imageId, UUID familyId, UUID userId) {
        StoryImage image = images.findById(imageId)
                .orElseThrow(() -> new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", HttpStatus.NOT_FOUND));
        if (image.getStatus() == StoryImageStatus.GENERATED || image.getStatus() == StoryImageStatus.FAILED) {
            return;
        }
        String prompt = image.getPromptText();
        long startedAt = System.nanoTime();
        try {
            GeneratedStoryImage generated = generator.generate(prompt);
            StoryImageIntegrity.Validation received = validateReceivedImage(image, generated);
            String storageKey = storage.saveStoryImage(generated.pngBytes(), image.getStory().getId().toString(), filename(image));
            verifyStoredImage(image, storageKey, received);
            image.markGenerated(storageKey, generated.model(), generated.size(), generated.quality());
            long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            logs.save(new AiImageGenerationLog(image.getStory().getCreatedBy(), image.getStory().getFamily(), image.getStory(), image, provider(generated), generated.model(), generated.quality(), generated.size(), StoryImageStatus.GENERATED, durationMs, costEstimator.estimate(generated.quality()), representedChapters(image), sanitizePrompt(prompt), null));
            LOGGER.info("story_image_generation imageId={} type={} chapters={} provider={} model={} status={} durationMs={}", image.getId(), image.getImageType(), representedChapters(image), provider(generated), generated.model(), StoryImageStatus.GENERATED, durationMs);
        } catch (IOException exception) {
            markFailed(image, "storage", exception, familyId, userId);
        } catch (RuntimeException exception) {
            markFailed(image, "openai", exception, familyId, userId);
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
        plans.add(new ImagePlan(StoryImageType.COVER, null, null, 0, "cover.png", base + "\nCENA PRINCIPAL:\nCapa encantadora da historia, mostrando os personagens principais no local central, clima de descoberta e afeto.\nComposicao segura para criancas, expressoes acolhedoras, sem texto, letras, legendas, logotipos ou marcas na imagem."));
        int sceneImages = Math.min(StoryLengthSpec.of(story.getLength()).sceneImages(), Math.max(0, properties.maxImages() - 1));
        for (int index = 0; index < sceneImages; index++) {
            int chapterStart = index * 2 + 1;
            int chapterEnd = Math.min(chapterStart + 1, chapters.size());
            String sceneText = groupedSceneText(chapters, chapterStart, chapterEnd);
            plans.add(new ImagePlan(StoryImageType.SCENE, chapterStart, chapterEnd, index + 1, "scene-" + (index + 1) + ".png", base + "\nCENA PRINCIPAL:\nEscolha um unico momento visual principal inspirado nos capitulos " + chapterStart + "-" + chapterEnd + ". " + sceneText + "\nEvite misturar acontecimentos diferentes na mesma imagem.\nComposicao segura para criancas, expressoes acolhedoras, sem texto, letras, legendas, logotipos ou marcas na imagem."));
        }
        return plans;
    }

    private String basePrompt(Story story) {
        return VISUAL_STYLE + "\n\n"
                + "FICHA FIXA DO PERSONAGEM:\n" + CharacterVisualProfile.from(story).toPromptText() + "\n\n"
                + "ROUPA FIXA:\n" + outfit(story) + "\n\n"
                + "CONSISTENCIA OBRIGATORIA:\nMantenha o mesmo rosto, idade aparente, cabelo, olhos, tom de pele, roupa e proporcoes em todas as ilustracoes desta historia. A consistencia e orientada por texto, sem referencia visual ou seed.\n\n"
                + "PERSONAGENS SECUNDARIOS:\n" + secondCharacter(story) + "\n\n"
                + "AMBIENTE:\n" + clean(firstNonBlank(story.getPlace(), "ambiente infantil acolhedor")) + "\n\n"
                + "TEMA:\n" + clean(story.getTheme());
    }

    private String outfit(Story story) {
        return "camiseta " + colorFor(story.getId()) + ", bermuda ou calca confortavel, tenis simples, roupas infantis praticas e consistentes em todas as imagens";
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
        return clean(story.getSecondCharacterName()) + ": personagem infantil ilustrado com aparencia generica segura, roupas simples e coloridas consistentes; nao inferir etnia.";
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

    private String filename(StoryImage image) {
        return image.getImageType() == StoryImageType.COVER ? "cover.png" : "scene-" + image.getSortOrder() + ".png";
    }

    private StoryImageIntegrity.Validation validateReceivedImage(StoryImage image, GeneratedStoryImage generated) throws IOException {
        byte[] bytes = generated == null ? null : generated.pngBytes();
        int receivedBytes = bytes == null ? 0 : bytes.length;
        LOGGER.info("story_image_received imageId={} receivedBytes={}", image.getId(), receivedBytes);
        StoryImageIntegrity.Validation validation = StoryImageIntegrity.validatePng(bytes);
        LOGGER.info("story_image_integrity imageId={} phase={} validPng={} shaMatch={} contentType={} width={} height={} bytes={}",
                image.getId(), "received", validation.valid(), true, validation.contentType(), validation.width(), validation.height(), validation.bytes());
        if (!validation.valid()) {
            throw new IOException("Invalid generated story image PNG: " + validation.reason());
        }
        return validation;
    }

    private void verifyStoredImage(StoryImage image, String storageKey, StoryImageIntegrity.Validation expected) throws IOException {
        StoredFile stored = storage.loadStoryImage(storageKey, expected.bytes());
        byte[] storedBytes;
        try (InputStream input = stored.resource().getInputStream()) {
            storedBytes = input.readAllBytes();
        }
        StoryImageIntegrity.Validation validation = StoryImageIntegrity.validatePng(storedBytes);
        boolean shaMatch = expected.sha256().equals(validation.sha256());
        LOGGER.info("story_image_stored imageId={} storageKey={} expectedBytes={} storedBytes={} contentType={}", image.getId(), storageKey, expected.bytes(), storedBytes.length, stored.contentType());
        LOGGER.info("story_image_integrity imageId={} phase={} validPng={} shaMatch={} contentType={} width={} height={} storageKey={}", image.getId(), "stored", validation.valid(), shaMatch, validation.contentType(), validation.width(), validation.height(), storageKey);
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
    private record ImagePlan(StoryImageType type, Integer chapterStart, Integer chapterEnd, int sortOrder, String filename, String prompt) {}
}
