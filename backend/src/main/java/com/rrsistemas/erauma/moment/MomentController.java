package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.shared.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class MomentController {
    private final MomentService momentService;
    private final CurrentUser currentUser;

    public MomentController(MomentService momentService, CurrentUser currentUser) {
        this.momentService = momentService;
        this.currentUser = currentUser;
    }

    @PostMapping("/families/{familyId}/moments")
    @ResponseStatus(HttpStatus.CREATED)
    MomentResponse create(@PathVariable UUID familyId, @Valid @RequestBody MomentRequest request) {
        return momentService.create(familyId, request, currentUser.get());
    }

    @GetMapping("/families/{familyId}/moments")
    PageResponse<MomentResponse> list(
            @PathVariable UUID familyId,
            @RequestParam(required = false) UUID childId,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return momentService.list(familyId, childId, favorite, from, to, page, size, currentUser.get());
    }

    @GetMapping("/families/{familyId}/moments/calendar")
    List<MomentCalendarDayResponse> calendar(
            @PathVariable UUID familyId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) UUID childId) {
        return momentService.calendar(familyId, childId, year, month, currentUser.get());
    }

    @GetMapping("/moments/{momentId}")
    MomentResponse get(@PathVariable UUID momentId) {
        return momentService.get(momentId, currentUser.get());
    }

    @PutMapping("/moments/{momentId}")
    MomentResponse update(@PathVariable UUID momentId, @Valid @RequestBody MomentRequest request) {
        return momentService.update(momentId, request, currentUser.get());
    }

    @PatchMapping("/moments/{momentId}/favorite")
    MomentResponse favorite(@PathVariable UUID momentId, @Valid @RequestBody FavoriteRequest request) {
        return momentService.favorite(momentId, request.favorite(), currentUser.get());
    }

    @DeleteMapping("/moments/{momentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID momentId) {
        momentService.delete(momentId, currentUser.get());
    }

    @PostMapping("/moments/{momentId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    List<MomentPhotoResponse> upload(@PathVariable UUID momentId, @RequestParam("files") List<MultipartFile> files) {
        return momentService.upload(momentId, files, currentUser.get());
    }

    @GetMapping("/moment-photos/{photoId}/content")
    ResponseEntity<Resource> content(@PathVariable UUID photoId) {
        return momentService.content(photoId, currentUser.get());
    }

    @DeleteMapping("/moment-photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePhoto(@PathVariable UUID photoId) {
        momentService.deletePhoto(photoId, currentUser.get());
    }
}
