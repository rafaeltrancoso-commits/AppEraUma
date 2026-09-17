package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildRequest;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.FileStorageService;
import com.rrsistemas.erauma.moment.StoredFile;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class StoryImageContentServiceTest {
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");

    private final StoryImageRepository images = mock(StoryImageRepository.class);
    private final FamilyService familyService = mock(FamilyService.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final StoryImageGenerationService imageGenerationService = mock(StoryImageGenerationService.class);
    private final StoryImageContentService service = new StoryImageContentService(images, familyService, storage, imageGenerationService);

    @Test
    void returnsBytesWhenFileExistsAndIsValid() {
        StoryImage image = generatedImage("story-images/scene-1.png");
        AppUser user = image.getStory().getCreatedBy();
        when(images.findByIdAndStory_ActiveTrue(image.getId())).thenReturn(Optional.of(image));
        when(familyService.requireMembership(image.getStory().getFamilyId(), user)).thenReturn(image.getStory().getFamily());
        when(storage.storyImageExists(image.getStorageKey())).thenReturn(true);
        when(storage.loadStoryImage(eq(image.getStorageKey()), anyLong())).thenReturn(new StoredFile(new ByteArrayResource(PNG_1X1), "image/png", PNG_1X1.length));

        var response = service.content(image.getId(), user);

        assertThat(response.getBody()).isEqualTo(PNG_1X1);
        verify(imageGenerationService, never()).reconcileMissingFile(any(), any());
    }

    @Test
    void reconcilesGeneratedImageWhenFileIsConfirmedMissingAndReturns404() {
        StoryImage image = generatedImage("story-images/scene-1.png");
        AppUser user = image.getStory().getCreatedBy();
        when(images.findByIdAndStory_ActiveTrue(image.getId())).thenReturn(Optional.of(image));
        when(familyService.requireMembership(image.getStory().getFamilyId(), user)).thenReturn(image.getStory().getFamily());
        when(storage.storyImageExists(image.getStorageKey())).thenReturn(false);
        when(storage.storyImageConfirmedMissing(image.getStorageKey())).thenReturn(true);
        when(storage.loadStoryImage(eq(image.getStorageKey()), anyLong()))
                .thenThrow(new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.content(image.getId(), user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "STORY_IMAGE_NOT_FOUND");

        verify(imageGenerationService, times(1)).reconcileMissingFile(image.getId(), image.getStorageKey());
    }

    @Test
    void doesNotReconcileOnATransientIoErrorThatCannotConfirmAbsence() {
        // Auditoria do storage (item 2 da reconciliacao): storyImageExists()=false pode significar
        // apenas que um erro de I/O transitorio impediu a checagem (ela devolve false nesse caso
        // tambem). A reconciliacao so pode disparar quando storyImageConfirmedMissing() confirma
        // a ausencia de verdade (Files.notExists) - aqui ele deliberadamente NAO confirma.
        StoryImage image = generatedImage("story-images/scene-1.png");
        AppUser user = image.getStory().getCreatedBy();
        when(images.findByIdAndStory_ActiveTrue(image.getId())).thenReturn(Optional.of(image));
        when(familyService.requireMembership(image.getStory().getFamilyId(), user)).thenReturn(image.getStory().getFamily());
        when(storage.storyImageExists(image.getStorageKey())).thenReturn(false);
        when(storage.storyImageConfirmedMissing(image.getStorageKey())).thenReturn(false);
        when(storage.loadStoryImage(eq(image.getStorageKey()), anyLong()))
                .thenThrow(new BusinessException("STORY_IMAGE_NOT_FOUND", "Imagem nao encontrada", org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.content(image.getId(), user)).isInstanceOf(BusinessException.class);

        verify(imageGenerationService, never()).reconcileMissingFile(any(), any());
    }

    @Test
    void doesNotReconcileWhenFailureHappensBeforeStorageIsChecked() {
        StoryImage image = generatedImage("story-images/scene-1.png");
        AppUser user = image.getStory().getCreatedBy();
        when(images.findByIdAndStory_ActiveTrue(image.getId())).thenReturn(Optional.of(image));
        when(familyService.requireMembership(image.getStory().getFamilyId(), user))
                .thenThrow(new BusinessException("FAMILY_FORBIDDEN", "Sem acesso", org.springframework.http.HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.content(image.getId(), user)).isInstanceOf(BusinessException.class);

        verify(imageGenerationService, never()).reconcileMissingFile(any(), any());
    }

    @Test
    void doesNotReconcileWhenImageIsAlreadyFailed() {
        StoryImage image = generatedImage("story-images/scene-1.png");
        image.markFailed("Falha anterior.");
        AppUser user = image.getStory().getCreatedBy();
        when(images.findByIdAndStory_ActiveTrue(image.getId())).thenReturn(Optional.of(image));
        when(familyService.requireMembership(image.getStory().getFamilyId(), user)).thenReturn(image.getStory().getFamily());

        assertThatThrownBy(() -> service.content(image.getId(), user)).isInstanceOf(BusinessException.class);

        verify(imageGenerationService, never()).reconcileMissingFile(any(), any());
    }

    private StoryImage generatedImage(String storageKey) {
        AppUser user = new AppUser("Mae", "mae@example.com", "hash");
        Family family = new Family("Familia", user);
        ChildProfile child = new ChildProfile(family, new ChildRequest("Rafael", LocalDate.now().minusYears(32), null, null, null, null, null, null, null, null, null, null));
        GeneratedStory generated = new GeneratedStory("Titulo", "Resumo", java.util.List.of(new GeneratedChapter(1, "Capitulo 1", "Conteudo do capitulo com uma tentativa e uma descoberta.")));
        StoryGenerateRequest request = new StoryGenerateRequest(child.getId(), null, "Rafael", null, "Aventura", "Jardim", null, StoryStyle.BEDTIME, StoryLength.SHORT, StoryGenerationMode.ILLUSTRATED);
        Story story = new Story(family, child, null, request, generated, user);
        StoryImage image = new StoryImage(story, story.getChapters().get(0), StoryImageType.COVER, "gpt-image-2", "1024x1024", "medium", 0, null, null, "prompt");
        story.getImages().add(image);
        image.markGenerating();
        image.markGenerated(storageKey, "gpt-image-2", "1024x1024", "medium");
        return image;
    }
}
