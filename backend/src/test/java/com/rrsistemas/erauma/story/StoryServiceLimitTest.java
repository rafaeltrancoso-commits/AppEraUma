package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildProfileRepository;
import com.rrsistemas.erauma.child.ChildRequest;
import com.rrsistemas.erauma.config.BusinessTime;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.moment.MomentRepository;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import com.rrsistemas.erauma.user.AppUserRepository;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Documents the illustrated-story daily limit accounting decision: "used" is derived from a
 * repository query over persisted stories (images not empty), not from an incremented counter.
 * That means the check is idempotent by construction against retries/concurrent double taps,
 * and a story is only ever "used" once its images collection actually exists (i.e. after text
 * generation succeeded and image records were planned) - never for a request rejected up front.
 */
class StoryServiceLimitTest {
    private final StoryRepository stories = mock(StoryRepository.class);
    private final ChildProfileRepository children = mock(ChildProfileRepository.class);
    private final FamilyService familyService = mock(FamilyService.class);
    private final StoryGenerator generator = mock(StoryGenerator.class);
    private final StoryImageGenerationService storyImageGenerationService = mock(StoryImageGenerationService.class);
    private final StoryGenerationProcessor storyGenerationProcessor = mock(StoryGenerationProcessor.class);
    private final AppUserRepository users = mock(AppUserRepository.class);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsIllustratedGenerationWhenDailyLimitAlreadyReachedWithoutPersistingAnything() {
        Family family = new Family("Familia", new AppUser("Mae", "mae@example.com", "hash"));
        ChildProfile child = new ChildProfile(family, new ChildRequest("Rafael", LocalDate.now().minusYears(32), null, null, null, null, null, null, null, null, null, null));
        StoryService service = service(new StoryAiProperties("mock", true, 10, 2, 3));
        when(familyService.requireMembership(eq(family.getId()), any())).thenReturn(family);
        when(familyService.requireMembershipForUpdate(eq(family.getId()), any())).thenReturn(family);
        when(children.findByIdAndActiveTrue(child.getId())).thenReturn(Optional.of(child));
        when(stories.countByCreatedBy_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any(), any())).thenReturn(0L);
        when(stories.countIllustratedByFamilyAndCreatedAtBetween(any(), any(), any())).thenReturn(2L);

        StoryGenerateRequest request = illustratedRequest(child.getId());
        AppUser user = new AppUser("Mae", "mae@example.com", "hash");
        when(users.findForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.generate(family.getId(), request, user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "STORY_ILLUSTRATED_DAILY_LIMIT_REACHED");

        verify(stories, never()).save(any());
    }

    @Test
    void allowsIllustratedGenerationWhenBelowDailyLimitAndPersistsExactlyOneStory() {
        Family family = new Family("Familia", new AppUser("Mae", "mae@example.com", "hash"));
        ChildProfile child = new ChildProfile(family, new ChildRequest("Rafael", LocalDate.now().minusYears(32), null, null, null, null, null, null, null, null, null, null));
        StoryService service = service(new StoryAiProperties("mock", true, 10, 2, 3));
        when(familyService.requireMembership(eq(family.getId()), any())).thenReturn(family);
        when(familyService.requireMembershipForUpdate(eq(family.getId()), any())).thenReturn(family);
        when(children.findByIdAndActiveTrue(child.getId())).thenReturn(Optional.of(child));
        when(stories.countByCreatedBy_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any(), any())).thenReturn(0L);
        when(stories.countIllustratedByFamilyAndCreatedAtBetween(any(), any(), any())).thenReturn(1L);
        when(stories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StoryGenerateRequest request = illustratedRequest(child.getId());
        AppUser user = new AppUser("Mae", "mae@example.com", "hash");
        when(users.findForUpdate(user.getId())).thenReturn(Optional.of(user));

        TransactionSynchronizationManager.initSynchronization();
        StoryResponse response = service.generate(family.getId(), request, user);

        assertThat(response.generationStatus()).isEqualTo(StoryGenerationStatus.PENDENTE);
        verify(stories, org.mockito.Mockito.times(1)).save(any());
        verify(users).findForUpdate(user.getId());
        verify(stories).countByCreatedBy_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                user.getId(), Instant.parse("2026-01-01T03:00:00Z"), Instant.parse("2026-01-02T03:00:00Z"));
        verify(stories).countIllustratedByFamilyAndCreatedAtBetween(
                family.getId(), Instant.parse("2026-01-01T03:00:00Z"), Instant.parse("2026-01-02T03:00:00Z"));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(familyService, users, stories);
        order.verify(familyService).requireMembership(eq(family.getId()), any());
        order.verify(users).findForUpdate(user.getId());
        order.verify(familyService).requireMembershipForUpdate(eq(family.getId()), any());
        order.verify(stories).countByCreatedBy_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any(), any());
        order.verify(stories).countIllustratedByFamilyAndCreatedAtBetween(any(), any(), any());
        order.verify(stories).save(any());
    }

    private StoryGenerateRequest illustratedRequest(java.util.UUID childId) {
        return new StoryGenerateRequest(null, null, null, null, "Aventura no jardim", "Jardim", null,
                StoryStyle.BEDTIME, StoryLength.SHORT, StoryGenerationMode.ILLUSTRATED, List.of(childId), null, null);
    }

    private StoryService service(StoryAiProperties properties) {
        return new StoryService(
                stories,
                children,
                mock(MomentRepository.class),
                familyService,
                generator,
                properties,
                mock(AiGenerationLogRepository.class),
                storyImageGenerationService,
                storyGenerationProcessor,
                users,
                new BusinessTime(ZoneId.of("America/Sao_Paulo"), Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"))),
                new StoryCharacterReferencePolicy());
    }
}
