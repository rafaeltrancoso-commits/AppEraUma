package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildProfileRepository;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.Moment;
import com.rrsistemas.erauma.moment.MomentRepository;
import com.rrsistemas.erauma.moment.PageResponse;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryService.class);
    private final StoryRepository stories;
    private final ChildProfileRepository children;
    private final MomentRepository moments;
    private final FamilyService familyService;
    private final StoryGenerator generator;
    private final StoryAiProperties storyAiProperties;
    private final AiGenerationLogRepository aiLogs;
    private final StoryImageGenerationService storyImageGenerationService;

    public StoryService(StoryRepository stories, ChildProfileRepository children, MomentRepository moments, FamilyService familyService, StoryGenerator generator, StoryAiProperties storyAiProperties, AiGenerationLogRepository aiLogs, StoryImageGenerationService storyImageGenerationService) {
        this.stories = stories;
        this.children = children;
        this.moments = moments;
        this.familyService = familyService;
        this.generator = generator;
        this.storyAiProperties = storyAiProperties;
        this.aiLogs = aiLogs;
        this.storyImageGenerationService = storyImageGenerationService;
    }

    @Transactional
    public StoryResponse generate(UUID familyId, StoryGenerateRequest request, AppUser user) {
        Family family = familyService.requireMembership(familyId, user);
        enforceDailyLimit(user);
        ChildProfile child = request.childId() == null ? null : requireFamilyChild(familyId, request.childId());
        Moment sourceMoment = request.sourceMomentId() == null ? null : requireFamilyMoment(familyId, request.sourceMomentId());
        String mainCharacterName = resolveMainCharacterName(request.mainCharacterName(), child);
        String secondCharacterName = normalizeCharacterName(request.secondCharacterName(), false);
        StoryGenerationRequest generationRequest = new StoryGenerationRequest(
                familyId,
                child == null ? null : child.getId(),
                child == null ? null : child.getName(),
                child == null ? null : child.getBirthDate(),
                mainCharacterName,
                secondCharacterName,
                sourceMoment == null ? null : sourceMoment.getId(),
                sourceMoment == null ? null : sourceMoment.getTitle(),
                sourceMoment == null ? null : sourceMoment.getDescription(),
                sourceMoment == null ? null : sourceMoment.getLocationName(),
                request.theme(),
                request.place(),
                request.favoriteAnimal(),
                request.style(),
                request.length());
        StoryGenerationMode requestedGenerationMode = generationMode(request);
        if (requestedGenerationMode == StoryGenerationMode.ILLUSTRATED) {
            enforceIllustratedDailyLimit(family);
        }
        long startedAt = System.nanoTime();
        try {
            GeneratedStory generated = generator.generate(generationRequest);
            StoryGenerateRequest resolvedRequest = new StoryGenerateRequest(request.childId(), request.sourceMomentId(), mainCharacterName, secondCharacterName, request.theme(), request.place(), request.favoriteAnimal(), request.style(), request.length(), requestedGenerationMode);
            Story story = stories.save(new Story(family, child, sourceMoment, resolvedRequest, generated, user));
            AiGenerationStatus status = "mock-fallback".equals(generated.provider()) ? AiGenerationStatus.FALLBACK : AiGenerationStatus.SUCCESS;
            aiLogs.save(new AiGenerationLog(user, family, story, generated, status));
            LOGGER.info("story_generation provider={} model={} status={} storyId={} durationMs={}", generated.provider(), generated.model(), status, story.getId(), generated.durationMs());
            if (requestedGenerationMode == StoryGenerationMode.ILLUSTRATED) {
                storyImageGenerationService.generateInitialImages(story, family, user);
            }
            return StoryResponse.from(story);
        } catch (AiConfigurationException | AiUnavailableException | AiGenerationException exception) {
            long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            aiLogs.save(new AiGenerationLog(user, family, storyAiProperties.generator(), null, AiGenerationStatus.FAILED, durationMs));
            LOGGER.warn("story_generation provider={} status={} durationMs={} reason={}", storyAiProperties.generator(), AiGenerationStatus.FAILED, durationMs, exception.getClass().getSimpleName());
            throw new BusinessException("AI_GENERATION_UNAVAILABLE", "Não conseguimos criar sua história agora. Tente novamente.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private StoryGenerationMode generationMode(StoryGenerateRequest request) {
        return request.generationMode() == null ? StoryGenerationMode.TEXT_ONLY : request.generationMode();
    }

    private void enforceIllustratedDailyLimit(Family family) {
        int limit = storyAiProperties.illustratedDailyLimit();
        java.time.ZonedDateTime startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault());
        java.time.Instant from = startOfDay.toInstant();
        java.time.Instant to = startOfDay.plusDays(1).toInstant();
        long used = stories.countIllustratedByFamilyAndCreatedAtBetween(family.getId(), from, to);
        boolean allowed = limit <= 0 || used < limit;
        LOGGER.info("illustrated_story_limit familyId={} used={} limit={} allowed={}", family.getId(), used, limit, allowed);
        if (!allowed) {
            throw new BusinessException("STORY_ILLUSTRATED_DAILY_LIMIT_REACHED", "Limite diário de histórias ilustradas atingido. Tente novamente amanhã.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private String resolveMainCharacterName(String requestedName, ChildProfile child) {
        String normalized = normalizeCharacterName(requestedName, false);
        if (normalized != null) {
            return normalized;
        }
        if (child != null && child.getName() != null && !child.getName().isBlank()) {
            return firstName(child.getName());
        }
        throw new BusinessException("MAIN_CHARACTER_REQUIRED", "Escolha uma criança ou informe o nome do personagem principal.", HttpStatus.BAD_REQUEST);
    }

    private String normalizeCharacterName(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new BusinessException("MAIN_CHARACTER_REQUIRED", "Escolha uma criança ou informe o nome do personagem principal.", HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new BusinessException("CHARACTER_NAME_TOO_LONG", "Nome do personagem deve ter no máximo 120 caracteres.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String firstName(String name) {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private void enforceDailyLimit(AppUser user) {
        int limit = storyAiProperties.dailyLimit();
        if (limit <= 0) {
            return;
        }
        java.time.Instant startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long count = aiLogs.countByUser_IdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay);
        if (count >= limit) {
            throw new BusinessException("STORY_DAILY_LIMIT_REACHED", "Limite diário de histórias atingido. Tente novamente amanhã.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<StoryResponse> list(UUID familyId, UUID childId, Boolean favorite, StoryStyle style, StoryGenerationMode generationMode, LocalDate from, LocalDate to, int page, int size, AppUser user) {
        familyService.requireMembership(familyId, user);
        if (childId != null) {
            requireFamilyChild(familyId, childId);
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        java.time.Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        java.time.Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return PageResponse.from(stories.search(familyId, childId, favorite, style, generationMode == null ? null : generationMode.name(), fromInstant, toInstant, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))).map(StoryResponse::from));
    }

    @Transactional(readOnly = true)
    public StoryResponse get(UUID storyId, AppUser user) {
        return StoryResponse.from(requireAllowed(storyId, user));
    }

    @Transactional
    public StoryResponse favorite(UUID storyId, boolean favorite, AppUser user) {
        Story story = requireAllowed(storyId, user);
        story.setFavorite(favorite);
        return StoryResponse.from(story);
    }

    @Transactional
    public StoryResponse update(UUID storyId, StoryUpdateRequest request, AppUser user) {
        Story story = requireAllowed(storyId, user);
        story.setTitle(request.title());
        if (request.favorite() != null) {
            story.setFavorite(request.favorite());
        }
        return StoryResponse.from(story);
    }

    @Transactional
    public void delete(UUID storyId, AppUser user) {
        requireAllowed(storyId, user).deactivate();
    }

    private Story requireAllowed(UUID storyId, AppUser user) {
        Story story = stories.findByIdAndActiveTrue(storyId)
                .orElseThrow(() -> new BusinessException("STORY_NOT_FOUND", "História não encontrada", HttpStatus.NOT_FOUND));
        familyService.requireMembership(story.getFamilyId(), user);
        return story;
    }

    private ChildProfile requireFamilyChild(UUID familyId, UUID childId) {
        ChildProfile child = children.findByIdAndActiveTrue(childId)
                .orElseThrow(() -> new BusinessException("CHILD_NOT_FOUND", "Criança não encontrada", HttpStatus.NOT_FOUND));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException("CHILD_NOT_FOUND", "Criança não encontrada", HttpStatus.NOT_FOUND);
        }
        return child;
    }

    private Moment requireFamilyMoment(UUID familyId, UUID momentId) {
        Moment moment = moments.findByIdAndActiveTrue(momentId)
                .orElseThrow(() -> new BusinessException("MOMENT_NOT_FOUND", "Momento não encontrado", HttpStatus.NOT_FOUND));
        if (!moment.getFamilyId().equals(familyId)) {
            throw new BusinessException("MOMENT_NOT_FOUND", "Momento não encontrado", HttpStatus.NOT_FOUND);
        }
        return moment;
    }
}
