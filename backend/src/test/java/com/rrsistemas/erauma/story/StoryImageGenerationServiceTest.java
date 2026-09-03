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
import com.rrsistemas.erauma.user.AppUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class StoryImageGenerationServiceTest {
    private final StoryImageRepository images = mock(StoryImageRepository.class);
    private final StoryImageGenerationService service = new StoryImageGenerationService(
            prompt -> new GeneratedStoryImage(new byte[] {1}, "mock-image", "1x1", "low", 1),
            mock(StoryRepository.class),
            images,
            mock(FamilyService.class),
            mock(AiImageGenerationLogRepository.class),
            mock(FileStorageService.class),
            new StoryImageProperties(true, 4, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
            new OpenAiImageProperties("gpt-image-2", "1024x1024", "medium", 60),
            new ImageCostEstimator(new StoryImageProperties(true, 4, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
            mock(PlatformTransactionManager.class),
            Runnable::run);

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
        assertThat(created.get(2).getChapterStart()).isEqualTo(3);
        assertThat(created.get(2).getChapterEnd()).isEqualTo(4);
        assertThat(created.get(3).getChapterStart()).isEqualTo(5);
        assertThat(created.get(3).getChapterEnd()).isEqualTo(6);
        assertThat(created).allSatisfy(image -> {
            assertThat(image.getPromptText()).contains("FICHA FIXA DO PERSONAGEM");
            assertThat(image.getPromptText()).contains("ROUPA FIXA");
            assertThat(image.getPromptText()).contains("CONSISTENCIA OBRIGATORIA");
        });
    }

    private List<StoryImage> createdImages(StoryLength length) {
        when(images.findByStory_IdAndImageTypeAndSortOrder(any(), any(), anyInt())).thenReturn(Optional.empty());
        when(images.save(any(StoryImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Story story = story(length);

        service.createInitialImageRecords(story);

        ArgumentCaptor<StoryImage> captor = ArgumentCaptor.forClass(StoryImage.class);
        org.mockito.Mockito.verify(images, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        org.mockito.Mockito.reset(images);
        return captor.getAllValues();
    }

    private Story story(StoryLength length) {
        AppUser user = new AppUser("Mae", "mae@example.com", "hash");
        Family family = new Family("Familia", user);
        ChildProfile child = new ChildProfile(family, new ChildRequest("Nando Teste", LocalDate.now().minusYears(5), "Nando", "Dinossauro", null, null, null, "castanho", "curto", null, "castanhos", "sorriso grande"));
        GeneratedStory generated = new GeneratedStory("Titulo", "Resumo", chapters(length));
        StoryGenerateRequest request = new StoryGenerateRequest(child.getId(), null, "Nando", "Luna", "Medo do escuro", "Jardim", "Dinossauro", StoryStyle.BEDTIME, length, StoryGenerationMode.ILLUSTRATED);
        return new Story(family, child, null, request, generated, user);
    }

    private List<GeneratedChapter> chapters(StoryLength length) {
        return java.util.stream.IntStream.rangeClosed(1, StoryLengthSpec.of(length).expectedChapters())
                .mapToObj(number -> new GeneratedChapter(number, "Capitulo " + number, "Conteudo do capitulo " + number + " com uma tentativa, uma descoberta e uma consequencia."))
                .toList();
    }
}
