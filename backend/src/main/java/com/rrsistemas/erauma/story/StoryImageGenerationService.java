package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.user.AppUser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryImageGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryImageGenerationService.class);
    private static final String STYLE = "ilustracao de livro infantil, acolhedora, colorida, suave, sem aparencia fotografica";
    private final StoryImageGenerator generator;
    private final StoryImageRepository images;
    private final AiImageGenerationLogRepository logs;
    private final FileStorageService storage;
    private final StoryImageProperties properties;
    private final OpenAiImageProperties openAiImageProperties;
    private final ImageCostEstimator costEstimator;
    private final Executor storyImageExecutor;

    public StoryImageGenerationService(
            StoryImageGenerator generator,
            StoryImageRepository images,
            AiImageGenerationLogRepository logs,
            FileStorageService storage,
            StoryImageProperties properties,
            OpenAiImageProperties openAiImageProperties,
            ImageCostEstimator costEstimator,
            @Qualifier("storyImageExecutor") Executor storyImageExecutor) {
        this.generator = generator;
        this.images = images;
        this.logs = logs;
        this.storage = storage;
        this.properties = properties;
        this.openAiImageProperties = openAiImageProperties;
        this.costEstimator = costEstimator;
        this.storyImageExecutor = storyImageExecutor;
    }

    @Transactional(noRollbackFor = {AiGenerationException.class, AiUnavailableException.class})
    public void generateInitialImages(Story story, Family family, AppUser user) {
        if (!properties.generationEnabled()) {
            return;
        }
        List<ImagePlan> plans = plans(story).stream().limit(Math.max(0, properties.maxImages())).toList();
        List<PlannedImage> plannedImages = new ArrayList<>();
        for (ImagePlan plan : plans) {
            StoryImage image = images.save(new StoryImage(story, plan.chapter(), plan.type(), openAiImageProperties.model(), openAiImageProperties.size(), openAiImageProperties.quality(), plan.sortOrder()));
            story.getImages().add(image);
            plannedImages.add(new PlannedImage(plan, image));
        }
        List<CompletableFuture<ImageGenerationResult>> futures = plannedImages.stream()
                .map(planned -> CompletableFuture.supplyAsync(() -> generate(planned.plan()), storyImageExecutor))
                .toList();
        for (int index = 0; index < plannedImages.size(); index++) {
            PlannedImage planned = plannedImages.get(index);
            try {
                ImageGenerationResult result = futures.get(index).join();
                if (result.failure() != null) {
                    throw result.failure();
                }
                GeneratedStoryImage generated = result.generated();
                String storageKey = storage.saveStoryImage(generated.pngBytes(), story.getId().toString(), planned.plan().filename());
                StoryImage image = planned.image();
                image.markGenerated(storageKey, generated.model(), generated.size(), generated.quality());
                logs.save(new AiImageGenerationLog(user, family, story, image, provider(generated), generated.model(), generated.quality(), generated.size(), StoryImageStatus.GENERATED, generated.durationMs(), costEstimator.estimate(generated.quality())));
                LOGGER.info("story_image_generation imageId={} type={} provider={} model={} status={} durationMs={}", image.getId(), image.getImageType(), provider(generated), generated.model(), StoryImageStatus.GENERATED, generated.durationMs());
            } catch (IOException exception) {
                StoryImage image = planned.image();
                image.markFailed();
                logs.save(new AiImageGenerationLog(user, family, story, image, "openai", openAiImageProperties.model(), openAiImageProperties.quality(), openAiImageProperties.size(), StoryImageStatus.FAILED, null, java.math.BigDecimal.ZERO));
                LOGGER.warn("story_image_generation imageId={} type={} provider={} model={} status={} durationMs={} reason={}", image.getId(), image.getImageType(), "openai", openAiImageProperties.model(), StoryImageStatus.FAILED, null, "storage");
            } catch (RuntimeException exception) {
                StoryImage image = planned.image();
                image.markFailed();
                logs.save(new AiImageGenerationLog(user, family, story, image, "openai", openAiImageProperties.model(), openAiImageProperties.quality(), openAiImageProperties.size(), StoryImageStatus.FAILED, null, java.math.BigDecimal.ZERO));
                LOGGER.warn("story_image_generation imageId={} type={} provider={} model={} status={} durationMs={} reason={}", image.getId(), image.getImageType(), "openai", openAiImageProperties.model(), StoryImageStatus.FAILED, null, exception.getClass().getSimpleName());
            }
        }
    }

    private ImageGenerationResult generate(ImagePlan plan) {
        try {
            return new ImageGenerationResult(generator.generate(plan.prompt()), null);
        } catch (RuntimeException exception) {
            return new ImageGenerationResult(null, exception);
        }
    }

    private List<ImagePlan> plans(Story story) {
        String base = basePrompt(story);
        List<StoryChapter> chapters = story.getChapters().stream().sorted(Comparator.comparingInt(StoryChapter::getChapterNumber)).toList();
        List<ImagePlan> plans = new ArrayList<>();
        plans.add(new ImagePlan(StoryImageType.COVER, null, 0, "cover.png", base + "\nCena: capa encantadora da historia, mostrando os personagens principais no local central, clima de descoberta e afeto. Sem texto escrito na imagem."));
        for (int index = 0; index < 2; index++) {
            StoryChapter chapter = index < chapters.size() ? chapters.get(index) : null;
            int sceneNumber = index + 1;
            String scenePrompt = chapter == null
                    ? base + "\nCena complementar " + sceneNumber + ": momento visual da historia mostrando continuidade, descoberta e afeto. " + clean(limit(firstNonBlank(story.getSummary(), story.getContent()), 700)) + "\nSem texto escrito na imagem."
                    : base + "\nCena do capitulo " + chapter.getChapterNumber() + ": " + clean(chapter.getTitle()) + ". " + clean(limit(chapter.getContent(), 700)) + "\nSem texto escrito na imagem.";
            plans.add(new ImagePlan(StoryImageType.SCENE, chapter, sceneNumber, "scene-" + sceneNumber + ".png", scenePrompt));
        }
        return plans;
    }

    private String basePrompt(Story story) {
        return "Estilo visual fixo: " + STYLE + ". Paleta consistente, roupas consistentes e composicao segura para criancas. "
                + CharacterVisualProfile.from(story).toPromptText() + " Roupas simples e coloridas, consistentes entre capa e cenas. "
                + secondCharacter(story)
                + "Local/contexto: " + clean(firstNonBlank(story.getPlace(), "ambiente infantil acolhedor")) + ". Tema narrativo: " + clean(story.getTheme()) + ".";
    }

    private String secondCharacter(Story story) {
        if (story.getSecondCharacterName() == null || story.getSecondCharacterName().isBlank()) {
            return "";
        }
        return "Segundo personagem: " + clean(story.getSecondCharacterName()) + ", personagem infantil ilustrado com aparencia generica segura, roupas simples e coloridas; nao inferir etnia. ";
    }

    private String clean(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim(); }
    private String firstNonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String limit(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
    private String provider(GeneratedStoryImage generated) { return generated.model() != null && generated.model().startsWith("mock") ? "mock" : "openai"; }
    private record ImagePlan(StoryImageType type, StoryChapter chapter, int sortOrder, String filename, String prompt) {}
    private record PlannedImage(ImagePlan plan, StoryImage image) {}
    private record ImageGenerationResult(GeneratedStoryImage generated, RuntimeException failure) {}
}
