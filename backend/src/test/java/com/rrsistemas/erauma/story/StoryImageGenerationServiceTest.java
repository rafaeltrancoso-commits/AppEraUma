package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildRequest;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.notification.PushNotificationService;
import com.rrsistemas.erauma.user.AppUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class StoryImageGenerationServiceTest {
    private final StoryImageRepository images = mock(StoryImageRepository.class);
    private final StoryImageGenerationService service = new StoryImageGenerationService(
            prompt -> new GeneratedStoryImage(new byte[] {1}, "mock-image", "1x1", "low", 1),
            mock(StoryRepository.class),
            images,
            mock(FamilyService.class),
            mock(AiImageGenerationLogRepository.class),
            mock(FileStorageService.class),
            new StoryImageProperties(true, 4, 3, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
            new OpenAiImageProperties("gpt-image-2", "1024x1024", "medium", 60),
            new ImageCostEstimator(new StoryImageProperties(true, 4, 3, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
            mock(PlatformTransactionManager.class),
            Runnable::run,
            mock(PushNotificationService.class),
            new StoryVisualStyle());

    @Test
    void createsImagePlansByStoryLengthWithoutOneImagePerChapter() {
        assertThat(createdImages(StoryLength.SHORT)).hasSize(2);
        assertThat(createdImages(StoryLength.MEDIUM)).hasSize(3);
        assertThat(createdImages(StoryLength.LONG)).hasSize(4);
    }

    @Test
    void groupsSceneImagesByTwoChaptersAndKeepsFixedVisualPrompt() {
        List<StoryImage> created = createdImages(StoryLength.LONG);

        assertThat(created.get(0).getImageType()).isEqualTo(StoryImageType.COVER);
        assertThat(created.get(1).getChapterStart()).isEqualTo(1);
        assertThat(created.get(1).getChapterEnd()).isEqualTo(2);
        assertThat(created.get(2).getChapterStart()).isEqualTo(2);
        assertThat(created.get(2).getChapterEnd()).isEqualTo(4);
        assertThat(created.get(3).getChapterStart()).isEqualTo(5);
        assertThat(created.get(3).getChapterEnd()).isEqualTo(6);
        assertThat(created).allSatisfy(image -> {
            assertThat(image.getPromptText()).contains("Ilustração cartoon infantil");
            assertThat(image.getPromptText()).contains("cores vivas e harmoniosas");
            assertThat(image.getPromptText()).contains("Sem realismo fotográfico");
            assertThat(image.getPromptText()).contains("texto, letras, números").contains("marca-d'água");
            assertThat(image.getPromptText()).contains("Não imite nem mencione artistas, estúdios");
            assertThat(image.getPromptText()).doesNotContain("Disney", "Pixar");
            assertThat(image.getPromptText()).contains("FICHAS VISUAIS CANONICAS");
            assertThat(image.getPromptText()).contains("ROUPA FIXA");
            assertThat(image.getPromptText()).contains("CONSISTENCIA OBRIGATORIA");
        });
        assertThat(created.get(0).getVisualFormat()).isEqualTo(StoryImageFormat.SINGLE_SCENE);
        assertThat(created.get(0).getPromptText()).contains("FORMATO SINGLE_SCENE OBRIGATORIO").contains("sem colagem").contains("sem texto");
        assertThat(created.get(2).getVisualFormat()).isEqualTo(StoryImageFormat.COMIC_THREE_PANELS);
        assertThat(created.get(2).getPromptText()).contains("Ilustração cartoon infantil").contains("Exatamente tres quadros").contains("QUADRO 1").contains("QUADRO 2").contains("QUADRO 3");
    }

    @Test
    void stripsProtectedCharacterReferenceFromSecondCharacterInImagePrompt() {
        List<StoryImage> created = createdImages(story(StoryLength.SHORT, "Homem Aranha", "Medo do escuro"));

        assertThat(created).allSatisfy(image -> {
            assertThat(image.getPromptText()).doesNotContainIgnoringCase("aranha");
            assertThat(image.getPromptText()).contains("Personagem secundario fictício e original");
        });
    }

    @Test
    void stripsProtectedCharacterReferenceFromThemeInImagePrompt() {
        List<StoryImage> created = createdImages(story(StoryLength.SHORT, "Luna", "Aventura com o Batman"));

        assertThat(created).allSatisfy(image -> {
            assertThat(image.getPromptText()).doesNotContainIgnoringCase("batman");
            assertThat(image.getPromptText()).contains("uma aventura infantil animada e original");
        });
    }

    @Test
    void adaptiveRetryRewritesPromptGenericallyEvenForTermNotInManualDenylist() {
        Story story = story(StoryLength.SHORT, "Capitao Trovao Supremo", "Aventura com o Capitao Trovao Supremo");
        StoryImage cover = createdImages(story).get(0);
        assertThat(cover.getPromptText()).contains("Capitao Trovao Supremo");

        cover.markFailed("moderation_blocked");
        service.rewriteForAdaptiveRetry(cover);

        assertThat(cover.getPromptText()).doesNotContainIgnoringCase("Trovao");
        assertThat(cover.getPromptText()).contains("Personagem secundario fictício e original");
        assertThat(cover.getPromptText()).contains("uma aventura infantil animada e original");
    }

    @Test
    void retryFailedImageAppliesAdaptiveRewriteOnlyWhenLastFailureWasModerationBlocked() {
        Story story = story(StoryLength.SHORT, "Capitao Trovao Supremo", "Aventura com o Capitao Trovao Supremo");
        StoryImage cover = createdImages(story).get(0);
        cover.markFailed("moderation_blocked");
        when(images.findForRetry(cover.getId())).thenReturn(Optional.of(cover));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.retryFailedImage(cover.getId(), user(story));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(cover.getPromptText()).doesNotContainIgnoringCase("Trovao");
        assertThat(cover.getStatus()).isEqualTo(StoryImageStatus.PENDING);
    }

    @Test
    void retryFailedImageKeepsOriginalPromptWhenLastFailureWasNotModerationBlocked() {
        Story story = story(StoryLength.SHORT, "Luna", "Medo do escuro");
        StoryImage cover = createdImages(story).get(0);
        String originalPrompt = cover.getPromptText();
        cover.markFailed("timeout");
        when(images.findForRetry(cover.getId())).thenReturn(Optional.of(cover));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.retryFailedImage(cover.getId(), user(story));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(cover.getPromptText()).isEqualTo(originalPrompt);
    }

    private AppUser user(Story story) {
        return story.getCreatedBy();
    }

    private List<StoryImage> createdImages(StoryLength length) {
        return createdImages(story(length));
    }

    private List<StoryImage> createdImages(Story story) {
        when(images.findByStory_IdAndImageTypeAndSortOrder(any(), any(), anyInt())).thenReturn(Optional.empty());
        when(images.save(any(StoryImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createInitialImageRecords(story);

        ArgumentCaptor<StoryImage> captor = ArgumentCaptor.forClass(StoryImage.class);
        org.mockito.Mockito.verify(images, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        org.mockito.Mockito.reset(images);
        return captor.getAllValues();
    }

    private Story story(StoryLength length) {
        return story(length, "Luna", "Medo do escuro");
    }

    private Story story(StoryLength length, String secondCharacterName, String theme) {
        AppUser user = new AppUser("Mae", "mae@example.com", "hash");
        Family family = new Family("Familia", user);
        ChildProfile child = new ChildProfile(family, new ChildRequest("Nando Teste", LocalDate.now().minusYears(5), "Nando", "Dinossauro", null, null, null, "castanho", "curto", null, "castanhos", "sorriso grande"));
        GeneratedStory generated = new GeneratedStory("Titulo", "Resumo", chapters(length));
        StoryGenerateRequest request = new StoryGenerateRequest(child.getId(), null, "Nando", secondCharacterName, theme, "Jardim", "Dinossauro", StoryStyle.BEDTIME, length, StoryGenerationMode.ILLUSTRATED);
        return new Story(family, child, null, request, generated, user);
    }

    private List<GeneratedChapter> chapters(StoryLength length) {
        return java.util.stream.IntStream.rangeClosed(1, StoryLengthSpec.of(length).expectedChapters())
                .mapToObj(number -> new GeneratedChapter(number, "Capitulo " + number, "Conteudo do capitulo " + number + " com uma tentativa, uma descoberta e uma consequencia."))
                .toList();
    }
}
