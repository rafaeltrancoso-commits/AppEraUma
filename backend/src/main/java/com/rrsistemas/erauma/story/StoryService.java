package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildProfileRepository;
import com.rrsistemas.erauma.config.BusinessTime;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.Moment;
import com.rrsistemas.erauma.moment.MomentRepository;
import com.rrsistemas.erauma.moment.PageResponse;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import com.rrsistemas.erauma.user.AppUserRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final StoryGenerationProcessor storyGenerationProcessor;
    private final AppUserRepository users;
    private final BusinessTime businessTime;

    public StoryService(StoryRepository stories, ChildProfileRepository children, MomentRepository moments, FamilyService familyService, StoryGenerator generator, StoryAiProperties storyAiProperties, AiGenerationLogRepository aiLogs, StoryImageGenerationService storyImageGenerationService, StoryGenerationProcessor storyGenerationProcessor, AppUserRepository users, BusinessTime businessTime) {
        this.stories = stories;
        this.children = children;
        this.moments = moments;
        this.familyService = familyService;
        this.generator = generator;
        this.storyAiProperties = storyAiProperties;
        this.aiLogs = aiLogs;
        this.storyImageGenerationService = storyImageGenerationService;
        this.storyGenerationProcessor = storyGenerationProcessor;
        this.users = users;
        this.businessTime = businessTime;
    }

    @Transactional
    public StoryResponse generate(UUID familyId, StoryGenerateRequest request, AppUser user) {
        StoryGenerationMode requestedGenerationMode = generationMode(request);
        Family family = familyService.requireMembership(familyId, user);
        // Serializa a contagem do limite diario por usuario, inclusive quando duas requisicoes
        // chegam para familias diferentes. O lock e mantido pela mesma transacao que conta e cria.
        users.findForUpdate(user.getId()).orElseThrow();
        if (requestedGenerationMode == StoryGenerationMode.ILLUSTRATED) {
            // Mantem tambem a reserva do limite ilustrado serializada por familia.
            family = familyService.requireMembershipForUpdate(familyId, user);
        }
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            var existing = stories.findByCreatedBy_IdAndIdempotencyKeyAndActiveTrue(user.getId(), idempotencyKey);
            if (existing.isPresent()) return StoryResponse.from(existing.get());
        }
        enforceDailyLimit(user);
        List<ChildProfile> selectedCharacters = resolveCharacters(familyId, request);
        ChildProfile child = selectedCharacters.get(0);
        Moment sourceMoment = request.sourceMomentId() == null ? null : requireFamilyMoment(familyId, request.sourceMomentId());
        String mainCharacterName = request.characterIds() == null
                ? resolveMainCharacterName(request.mainCharacterName(), child)
                : firstName(firstNonBlank(child.getNickname(), child.getName()));
        String otherCharacters = normalizeOtherCharacters(firstNonBlank(request.otherCharacters(), request.secondCharacterName()), selectedCharacters);
        String secondCharacterName = request.characterIds() == null ? normalizeCharacterName(request.secondCharacterName(), false) : otherCharacters;
        String favoriteAnimal = firstNonBlank(request.favoriteAnimal(), child.getFavoriteAnimal());
        String place = firstNonBlank(request.place(), sourceMoment == null ? null : sourceMoment.getLocationName());
        if (requestedGenerationMode == StoryGenerationMode.ILLUSTRATED) {
            enforceIllustratedDailyLimit(family);
        }
        StoryGenerateRequest resolved = new StoryGenerateRequest(child.getId(), request.sourceMomentId(), mainCharacterName,
                secondCharacterName, request.theme(), place, favoriteAnimal, request.style(), request.length(),
                requestedGenerationMode, selectedCharacters.stream().map(ChildProfile::getId).toList(), otherCharacters, idempotencyKey);
        Story story = Story.pending(family, child, sourceMoment, resolved, user);
        for (int index = 0; index < selectedCharacters.size(); index++) {
            ChildProfile profile = selectedCharacters.get(index);
            story.addCharacter(profile, index + 1, CharacterVisualProfile.from(profile).toPromptText());
        }
        stories.save(story);
        scheduleGenerationAfterCommit(story.getId());
        return StoryResponse.from(story);
    }

    @Transactional
    public StoryResponse generateLegacy(UUID familyId, StoryGenerateRequest request, AppUser user) {
        StoryGenerationMode mode = generationMode(request);
        Family family = familyService.requireMembership(familyId, user);
        users.findForUpdate(user.getId()).orElseThrow();
        if (mode == StoryGenerationMode.ILLUSTRATED) {
            family = familyService.requireMembershipForUpdate(familyId, user);
        }
        enforceDailyLimit(user);
        ChildProfile child = requireRequestedChild(familyId, request.childId());
        Moment sourceMoment = request.sourceMomentId() == null ? null : requireFamilyMoment(familyId, request.sourceMomentId());
        String mainCharacterName = resolveMainCharacterName(request.mainCharacterName(), child);
        String secondCharacterName = normalizeCharacterName(request.secondCharacterName(), false);
        String favoriteAnimal = firstNonBlank(request.favoriteAnimal(), child.getFavoriteAnimal());
        String place = firstNonBlank(request.place(), sourceMoment == null ? null : sourceMoment.getLocationName());
        if (mode == StoryGenerationMode.ILLUSTRATED) enforceIllustratedDailyLimit(family);
        StoryGenerationRequest generationRequest = new StoryGenerationRequest(familyId, child.getId(), child.getName(), child.getBirthDate(),
                mainCharacterName, secondCharacterName, sourceMoment == null ? null : sourceMoment.getId(),
                sourceMoment == null ? null : sourceMoment.getTitle(), sourceMoment == null ? null : sourceMoment.getDescription(),
                sourceMoment == null ? null : sourceMoment.getLocationName(), request.theme(), place, favoriteAnimal, request.style(), request.length());
        long startedAt = System.nanoTime();
        try {
            GeneratedStory generated = generator.generate(generationRequest);
            StoryGenerateRequest resolved = new StoryGenerateRequest(child.getId(), request.sourceMomentId(), mainCharacterName,
                    secondCharacterName, request.theme(), place, favoriteAnimal, request.style(), request.length(), mode);
            Story story = new Story(family, child, sourceMoment, resolved, generated, user);
            story.addCharacter(child, 1, CharacterVisualProfile.from(child).toPromptText());
            stories.save(story);
            aiLogs.save(new AiGenerationLog(user, family, story, generated,
                    "mock-fallback".equals(generated.provider()) ? AiGenerationStatus.FALLBACK : AiGenerationStatus.SUCCESS));
            if (mode == StoryGenerationMode.ILLUSTRATED && storyImageGenerationService.isGenerationEnabled()) {
                story.markProcessingImages();
                storyImageGenerationService.createInitialImageRecords(story);
                scheduleImageGenerationAfterCommit(story.getId(), family.getId(), user.getId());
            }
            return StoryResponse.from(story);
        } catch (AiConfigurationException | AiUnavailableException | AiGenerationException exception) {
            long durationMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            aiLogs.save(new AiGenerationLog(user, family, storyAiProperties.generator(), null, AiGenerationStatus.FAILED, durationMs));
            throw new BusinessException("AI_GENERATION_UNAVAILABLE", "Não conseguimos criar sua história agora. Tente novamente.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private List<ChildProfile> resolveCharacters(UUID familyId, StoryGenerateRequest request) {
        List<UUID> ids = request.characterIds() == null || request.characterIds().isEmpty()
                ? (request.childId() == null ? List.of() : List.of(request.childId())) : request.characterIds();
        if (ids.isEmpty()) {
            String code = request.characterIds() == null ? "CHILD_REQUIRED" : "CHARACTER_REQUIRED";
            throw new BusinessException(code, "Escolha ao menos um personagem.", HttpStatus.BAD_REQUEST);
        }
        if (ids.size() > 3) throw new BusinessException("CHARACTER_LIMIT_EXCEEDED", "Escolha no máximo três personagens.", HttpStatus.BAD_REQUEST);
        if (new LinkedHashSet<>(ids).size() != ids.size()) throw new BusinessException("CHARACTER_DUPLICATED", "O mesmo personagem não pode ser selecionado mais de uma vez.", HttpStatus.BAD_REQUEST);
        return ids.stream().map(id -> requireFamilyChild(familyId, id)).toList();
    }

    private String normalizeOtherCharacters(String value, List<ChildProfile> selected) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 500) throw new BusinessException("OTHER_CHARACTERS_TOO_LONG", "Outros personagens deve ter no máximo 500 caracteres.", HttpStatus.BAD_REQUEST);
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        boolean duplicates = selected.stream().map(profile -> firstName(firstNonBlank(profile.getNickname(), profile.getName())).toLowerCase(java.util.Locale.ROOT))
                .anyMatch(name -> lower.matches(".*\\b" + java.util.regex.Pattern.quote(name) + "\\b.*"));
        if (duplicates) throw new BusinessException("OTHER_CHARACTERS_DUPLICATED", "Remova de Outros personagens quem já foi selecionado.", HttpStatus.BAD_REQUEST);
        return normalized;
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 120) throw new BusinessException("IDEMPOTENCY_KEY_INVALID", "Identificador da solicitação inválido.", HttpStatus.BAD_REQUEST);
        return normalized;
    }

    private void scheduleGenerationAfterCommit(UUID storyId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storyGenerationProcessor.processAsync(storyId); }
        });
    }

    private StoryGenerationMode generationMode(StoryGenerateRequest request) {
        return request.generationMode() == null ? StoryGenerationMode.TEXT_ONLY : request.generationMode();
    }

    private void enforceIllustratedDailyLimit(Family family) {
        int limit = storyAiProperties.illustratedDailyLimit();
        BusinessTime.DayRange day = businessTime.currentDay();
        long used = stories.countIllustratedByFamilyAndCreatedAtBetween(family.getId(), day.fromInclusive(), day.toExclusive());
        boolean allowed = limit <= 0 || used < limit;
        LOGGER.info("illustrated_story_limit familyId={} used={} limit={} allowed={}", family.getId(), used, limit, allowed);
        if (!allowed) {
            throw new BusinessException("STORY_ILLUSTRATED_DAILY_LIMIT_REACHED", "Limite diario de historias ilustradas atingido. Tente novamente amanha.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private String resolveMainCharacterName(String requestedName, ChildProfile child) {
        String normalized = normalizeCharacterName(requestedName, false);
        if (normalized != null) {
            return normalized;
        }
        if (child.getName() != null && !child.getName().isBlank()) {
            return firstName(child.getName());
        }
        throw new BusinessException("MAIN_CHARACTER_REQUIRED", "Informe o personagem principal.", HttpStatus.BAD_REQUEST);
    }

    private String normalizeCharacterName(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new BusinessException("MAIN_CHARACTER_REQUIRED", "Informe o personagem principal.", HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new BusinessException("CHARACTER_NAME_TOO_LONG", "Nome do personagem deve ter no maximo 120 caracteres.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String firstName(String name) {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private ChildProfile requireRequestedChild(UUID familyId, UUID childId) {
        if (childId == null) {
            throw new BusinessException("CHILD_REQUIRED", "Escolha um personagem para personalizar a história.", HttpStatus.BAD_REQUEST);
        }
        return requireFamilyChild(familyId, childId);
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private void scheduleImageGenerationAfterCommit(UUID storyId, UUID familyId, UUID userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storyImageGenerationService.processStoryImagesAsync(storyId, familyId, userId);
            }
        });
    }

    private void enforceDailyLimit(AppUser user) {
        int limit = storyAiProperties.dailyLimit();
        if (limit <= 0) {
            return;
        }
        BusinessTime.DayRange day = businessTime.currentDay();
        long count = stories.countByCreatedBy_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                user.getId(), day.fromInclusive(), day.toExclusive());
        if (count >= limit) {
            throw new BusinessException("STORY_DAILY_LIMIT_REACHED", "Limite diario de historias atingido. Tente novamente amanha.", HttpStatus.TOO_MANY_REQUESTS);
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
        java.time.Instant fromInstant = from == null ? null : businessTime.day(from).fromInclusive();
        java.time.Instant toInstant = to == null ? null : businessTime.day(to).toExclusive();
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
    public StoryResponse retryGeneration(UUID storyId, AppUser user) {
        Story story = requireAllowed(storyId, user);
        if (story.getGenerationStatus() != StoryGenerationStatus.ERRO) {
            throw new BusinessException("STORY_RETRY_NOT_ALLOWED", "Somente histórias com erro podem ser reenviadas.", HttpStatus.BAD_REQUEST);
        }
        if (story.getGenerationAttemptCount() >= storyAiProperties.effectiveMaxAttempts()) {
            throw new BusinessException("STORY_ATTEMPT_LIMIT_REACHED", "Esta história atingiu o limite de tentativas.", HttpStatus.TOO_MANY_REQUESTS);
        }
        story.retryGeneration();
        scheduleGenerationAfterCommit(story.getId());
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
                .orElseThrow(() -> new BusinessException("STORY_NOT_FOUND", "Historia nao encontrada", HttpStatus.NOT_FOUND));
        familyService.requireMembership(story.getFamilyId(), user);
        return story;
    }

    private ChildProfile requireFamilyChild(UUID familyId, UUID childId) {
        ChildProfile child = children.findByIdAndActiveTrue(childId)
                .orElseThrow(() -> new BusinessException("CHILD_NOT_FOUND", "Personagem não encontrado", HttpStatus.NOT_FOUND));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException("CHILD_NOT_FOUND", "Personagem não encontrado", HttpStatus.NOT_FOUND);
        }
        return child;
    }

    private Moment requireFamilyMoment(UUID familyId, UUID momentId) {
        Moment moment = moments.findByIdAndActiveTrue(momentId)
                .orElseThrow(() -> new BusinessException("MOMENT_NOT_FOUND", "Momento nao encontrado", HttpStatus.NOT_FOUND));
        if (!moment.getFamilyId().equals(familyId)) {
            throw new BusinessException("MOMENT_NOT_FOUND", "Momento nao encontrado", HttpStatus.NOT_FOUND);
        }
        return moment;
    }
}
