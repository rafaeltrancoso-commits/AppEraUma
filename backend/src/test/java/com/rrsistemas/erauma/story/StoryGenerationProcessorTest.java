package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rrsistemas.erauma.child.ChildProfile;
import com.rrsistemas.erauma.child.ChildRequest;
import com.rrsistemas.erauma.family.Family;
import com.rrsistemas.erauma.notification.PushNotificationService;
import com.rrsistemas.erauma.user.AppUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Item 16 do checklist de auditoria: uma falha definitiva de validacao narrativa (depois do
 * retry interno do OpenAIStoryGenerator ja ter se esgotado e propagado a excecao) nunca pode
 * deixar a historia presa em PROCESSANDO_TEXTO - ela precisa terminar em ERRO, com uma mensagem
 * amigavel e sem stack trace/detalhe interno no campo exposto ao mobile.
 */
class StoryGenerationProcessorTest {
    private static final PlatformTransactionManager NOOP_TRANSACTIONS = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    };

    private final StoryRepository stories = mock(StoryRepository.class);
    private final StoryGenerator generator = mock(StoryGenerator.class);
    private final StoryImageGenerationService imageService = mock(StoryImageGenerationService.class);

    @Test
    void definitiveCharacterMissingFailureLeavesStoryInErroNotStuckProcessing() {
        Story story = pendingStory();
        when(stories.findForGeneration(story.getId())).thenReturn(Optional.of(story));
        when(stories.findByIdAndActiveTrue(story.getId())).thenReturn(Optional.of(story));
        when(generator.generate(any())).thenThrow(new StoryNarrativeValidationException(
                "CHARACTER_MISSING", "Personagens selecionados ausentes da narrativa: Thamires.", List.of("Thamires")));
        StoryGenerationProcessor processor = processor();

        processor.process(story.getId());

        assertThat(story.getGenerationStatus()).isEqualTo(StoryGenerationStatus.ERRO);
        assertThat(story.getGenerationStatus()).isNotEqualTo(StoryGenerationStatus.PROCESSANDO_TEXTO);
        assertThat(story.getGenerationError())
                .doesNotContain("StoryNarrativeValidationException", "CHARACTER_MISSING", "Thamires");
    }

    private StoryGenerationProcessor processor() {
        return new StoryGenerationProcessor(
                stories,
                generator,
                mock(AiGenerationLogRepository.class),
                new StoryAiProperties("openai", true, 10, 2, 3),
                imageService,
                NOOP_TRANSACTIONS,
                Runnable::run,
                mock(PushNotificationService.class));
    }

    private Story pendingStory() {
        AppUser user = new AppUser("Mae", "mae@example.com", "hash");
        Family family = new Family("Familia", user);
        ChildProfile child = new ChildProfile(family, new ChildRequest("Fernando", LocalDate.now().minusYears(5), null, null, null, null, null, null, null, null, null, null));
        StoryGenerateRequest request = new StoryGenerateRequest(child.getId(), null, "Fernando", null, "Aventura", "Jardim", null, StoryStyle.BEDTIME, StoryLength.SHORT, StoryGenerationMode.TEXT_ONLY);
        Story story = Story.pending(family, child, null, request, user);
        story.addCharacter(child, 1, "descricao visual");
        return story;
    }
}
