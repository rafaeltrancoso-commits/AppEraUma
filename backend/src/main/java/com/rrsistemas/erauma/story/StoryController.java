package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.moment.PageResponse;
import com.rrsistemas.erauma.shared.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StoryController {
    private final StoryService storyService;
    private final StoryImageContentService storyImageContentService;
    private final CurrentUser currentUser;

    public StoryController(StoryService storyService, StoryImageContentService storyImageContentService, CurrentUser currentUser) {
        this.storyService = storyService;
        this.storyImageContentService = storyImageContentService;
        this.currentUser = currentUser;
    }

    @PostMapping("/families/{familyId}/stories/generate")
    @ResponseStatus(HttpStatus.CREATED)
    StoryResponse generate(@PathVariable UUID familyId, @Valid @RequestBody StoryGenerateRequest request) {
        return storyService.generate(familyId, request, currentUser.get());
    }

    @GetMapping("/families/{familyId}/stories")
    PageResponse<StoryResponse> list(
            @PathVariable UUID familyId,
            @RequestParam(required = false) UUID childId,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) StoryStyle style,
            @RequestParam(required = false) StoryGenerationMode generationMode,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return storyService.list(familyId, childId, favorite, style, generationMode, from, to, page, size, currentUser.get());
    }

    @GetMapping("/stories/{storyId}")
    StoryResponse get(@PathVariable UUID storyId) {
        return storyService.get(storyId, currentUser.get());
    }

    @PatchMapping("/stories/{storyId}/favorite")
    StoryResponse favorite(@PathVariable UUID storyId, @Valid @RequestBody StoryFavoriteRequest request) {
        return storyService.favorite(storyId, request.favorite(), currentUser.get());
    }

    @PutMapping("/stories/{storyId}")
    StoryResponse update(@PathVariable UUID storyId, @Valid @RequestBody StoryUpdateRequest request) {
        return storyService.update(storyId, request, currentUser.get());
    }

    @DeleteMapping("/stories/{storyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID storyId) {
        storyService.delete(storyId, currentUser.get());
    }

    @GetMapping("/story-images/{imageId}/content")
    ResponseEntity<byte[]> imageContent(@PathVariable UUID imageId) {
        return storyImageContentService.content(imageId, currentUser.get());
    }
}
