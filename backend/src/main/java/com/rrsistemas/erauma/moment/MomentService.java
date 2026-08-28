package com.rrsistemas.erauma.moment;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildProfileRepository;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.story.StoryRepository;
import com.rrsistemas.erauma.user.AppUser;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.criteria.JoinType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MomentService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final MomentRepository moments;
    private final MomentPhotoRepository photos;
    private final ChildProfileRepository children;
    private final StoryRepository stories;
    private final FamilyService familyService;
    private final FileStorageService storage;
    private final long maxFileSizeBytes;
    private final int maxPhotos;

    public MomentService(
            MomentRepository moments,
            MomentPhotoRepository photos,
            ChildProfileRepository children,
            StoryRepository stories,
            FamilyService familyService,
            FileStorageService storage,
            @Value("${app.storage.max-file-size-mb:10}") long maxFileSizeMb,
            @Value("${app.moment.max-photos:10}") int maxPhotos) {
        this.moments = moments;
        this.photos = photos;
        this.children = children;
        this.stories = stories;
        this.familyService = familyService;
        this.storage = storage;
        this.maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
        this.maxPhotos = maxPhotos;
    }

    @Transactional
    public MomentResponse create(UUID familyId, MomentRequest request, AppUser user) {
        Family family = familyService.requireMembership(familyId, user);
        Moment moment = moments.save(new Moment(family, user, request));
        applyRelations(moment, request);
        return MomentResponse.from(moment);
    }

    @Transactional(readOnly = true)
    public PageResponse<MomentResponse> list(UUID familyId, UUID childId, Boolean favorite, LocalDate from, LocalDate to, int page, int size, AppUser user) {
        familyService.requireMembership(familyId, user);
        if (childId != null) {
            requireFamilyChild(familyId, childId);
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        LocalDateTime fromDateTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toDateTime = to == null ? null : to.plusDays(1).atStartOfDay();
        return PageResponse.from(moments.findAll(momentFilter(familyId, childId, favorite, fromDateTime, toDateTime), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(MomentResponse::from));
    }

    @Transactional(readOnly = true)
    public MomentResponse get(UUID momentId, AppUser user) {
        Moment moment = requireAllowed(momentId, user);
        return MomentResponse.from(moment, stories.findBySourceMoment_IdAndActiveTrueOrderByCreatedAtDesc(moment.getId()));
    }

    @Transactional(readOnly = true)
    public List<MomentCalendarDayResponse> calendar(UUID familyId, UUID childId, int year, int month, AppUser user) {
        familyService.requireMembership(familyId, user);
        if (childId != null) {
            requireFamilyChild(familyId, childId);
        }
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = start.plusMonths(1).atStartOfDay();
        return moments.calendar(familyId, childId, from, to).stream()
                .map(item -> new MomentCalendarDayResponse(item.getDate(), item.getCount()))
                .toList();
    }

    @Transactional
    public MomentResponse update(UUID momentId, MomentRequest request, AppUser user) {
        Moment moment = requireAllowed(momentId, user);
        moment.apply(request);
        moment.clearRelations();
        moments.flush();
        applyRelations(moment, request);
        return MomentResponse.from(moment);
    }

    @Transactional
    public MomentResponse favorite(UUID momentId, boolean favorite, AppUser user) {
        Moment moment = requireAllowed(momentId, user);
        moment.setFavorite(favorite);
        return MomentResponse.from(moment);
    }

    @Transactional
    public void delete(UUID momentId, AppUser user) {
        requireAllowed(momentId, user).deactivate();
    }

    @Transactional
    public List<MomentPhotoResponse> upload(UUID momentId, List<MultipartFile> files, AppUser user) {
        Moment moment = requireAllowed(momentId, user);
        if (files == null || files.isEmpty()) {
            throw new BusinessException("PHOTO_REQUIRED", "Informe ao menos uma foto", HttpStatus.BAD_REQUEST);
        }
        long current = photos.countByMoment_IdAndActiveTrue(moment.getId());
        if (current + files.size() > maxPhotos) {
            throw new BusinessException("PHOTO_LIMIT_EXCEEDED", "Limite de fotos do momento excedido", HttpStatus.BAD_REQUEST);
        }
        int sortOrder = (int) current;
        for (MultipartFile file : files) {
            validatePhoto(file);
            try {
                String storageKey = storage.save(file);
                photos.save(new MomentPhoto(moment, storageKey, cleanFilename(file.getOriginalFilename()), file.getContentType(), file.getSize(), sortOrder++));
            } catch (IOException exception) {
                throw new BusinessException("PHOTO_STORAGE_ERROR", "Não foi possível salvar a foto", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return photos.findByMoment_IdAndActiveTrueOrderBySortOrderAsc(moment.getId()).stream().map(MomentPhotoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> content(UUID photoId, AppUser user) {
        MomentPhoto photo = requireAllowedPhoto(photoId, user);
        StoredFile stored = storage.load(photo.getStorageKey(), photo.getContentType(), photo.getSizeBytes());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.sizeBytes())
                .body(stored.resource());
    }

    @Transactional
    public void deletePhoto(UUID photoId, AppUser user) {
        requireAllowedPhoto(photoId, user).deactivate();
    }

    private Moment requireAllowed(UUID momentId, AppUser user) {
        Moment moment = moments.findByIdAndActiveTrue(momentId)
                .orElseThrow(() -> new BusinessException("MOMENT_NOT_FOUND", "Momento não encontrado", HttpStatus.NOT_FOUND));
        familyService.requireMembership(moment.getFamilyId(), user);
        return moment;
    }

    private MomentPhoto requireAllowedPhoto(UUID photoId, AppUser user) {
        MomentPhoto photo = photos.findByIdAndActiveTrue(photoId)
                .orElseThrow(() -> new BusinessException("PHOTO_NOT_FOUND", "Foto não encontrada", HttpStatus.NOT_FOUND));
        familyService.requireMembership(photo.getMoment().getFamilyId(), user);
        if (!photo.getMoment().isActive()) {
            throw new BusinessException("PHOTO_NOT_FOUND", "Foto não encontrada", HttpStatus.NOT_FOUND);
        }
        return photo;
    }

    private void applyRelations(Moment moment, MomentRequest request) {
        List<MomentChild> momentChildren = distinct(request.childIds()).stream()
                .map(childId -> new MomentChild(moment, requireFamilyChild(moment.getFamilyId(), childId)))
                .toList();
        moment.addChildren(momentChildren);
        List<MomentParticipant> participants = request.participants() == null ? List.of() : request.participants().stream()
                .filter(participant -> participant.name() != null && !participant.name().isBlank())
                .map(participant -> new MomentParticipant(moment, participant.name(), participant.participantType()))
                .toList();
        moment.addParticipants(participants);
    }

    private ChildProfile requireFamilyChild(UUID familyId, UUID childId) {
        ChildProfile child = children.findByIdAndActiveTrue(childId)
                .orElseThrow(() -> new BusinessException("CHILD_NOT_FOUND", "Criança não encontrada", HttpStatus.NOT_FOUND));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException("CHILD_NOT_FOUND", "Criança não encontrada", HttpStatus.NOT_FOUND);
        }
        return child;
    }

    private Set<UUID> distinct(List<UUID> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private Specification<Moment> momentFilter(UUID familyId, UUID childId, Boolean favorite, LocalDateTime from, LocalDateTime to) {
        return (root, query, criteria) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteria.equal(root.get("family").get("id"), familyId));
            predicates.add(criteria.isTrue(root.get("active")));
            if (favorite != null) {
                predicates.add(criteria.equal(root.get("favorite"), favorite));
            }
            if (childId != null) {
                predicates.add(criteria.equal(root.join("children", JoinType.INNER).get("child").get("id"), childId));
            }
            if (from != null) {
                predicates.add(criteria.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(criteria.lessThan(root.get("occurredAt"), to));
            }
            return criteria.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private void validatePhoto(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("INVALID_FILE", "Arquivo inválido", HttpStatus.BAD_REQUEST);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException("INVALID_FILE_TYPE", "Tipo de foto não permitido", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException("PHOTO_TOO_LARGE", "A foto deve ter no máximo 10 MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null) {
            return null;
        }
        return PathSafe.filename(filename);
    }
}
