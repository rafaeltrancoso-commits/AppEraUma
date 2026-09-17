package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryNarrativeValidatorTest {
    private final StoryNarrativeValidator validator = new StoryNarrativeValidator();

    @Test
    void findsCharacterRegardlessOfCase() {
        validator.validateCharacters(storyWithText("FERNANDO chegou correndo."), List.of(character("Fernando", null)));
    }

    @Test
    void findsCharacterWithAccentWhenNarrativeTextHasNoAccent() {
        validator.validateCharacters(storyWithText("Jose olhou para o ceu estrelado."), List.of(character("José", null)));
    }

    @Test
    void findsFatherCharacterByFirstNameWhenTextOmitsTheAffectivePrefix() {
        validator.validateCharacters(storyWithText("Rafael contou uma historia antes de dormir."), List.of(character("Papai Rafael", null)));
    }

    @Test
    void findsMotherCharacterByFirstNameWhenTextOmitsTheAffectivePrefix() {
        validator.validateCharacters(storyWithText("Thamires preparou um lanche especial."), List.of(character("Mamãe Thamires", null)));
    }

    @Test
    void findsGrandmotherCharacterByFirstNameWithoutAccentWhenTextOmitsThePrefix() {
        validator.validateCharacters(storyWithText("Lucia contou uma historia de ninar."), List.of(character("Vovó Lúcia", null)));
    }

    @Test
    void findsCharacterByRegisteredNickname() {
        validator.validateCharacters(storyWithText("Nandinho correu pelo jardim."), List.of(character("Fernando Trancoso", "Nandinho")));
    }

    @Test
    void findsCharacterByFullNameWhenTextUsesItLiterally() {
        validator.validateCharacters(storyWithText("Fernando Trancoso chegou em casa feliz."), List.of(character("Fernando Trancoso", null)));
    }

    @Test
    void partialWordMatchInsideAnotherWordDoesNotCountAsPresent() {
        assertThatThrownBy(() -> validator.validateCharacters(
                storyWithText("Ela comeu uma banana no cafe da manha."), List.of(character("Ana", null))))
                .isInstanceOfSatisfying(StoryNarrativeValidationException.class,
                        exception -> assertThat(exception.missingCharacters()).containsExactly("Ana"));
    }

    @Test
    void ambiguousAffectivePrefixAloneDoesNotValidateEitherCharacter() {
        StoryCharacterPrompt fatherA = character("Papai Rafael", null);
        StoryCharacterPrompt fatherB = character("Papai Ricardo", null);

        assertThatThrownBy(() -> validator.validateCharacters(
                storyWithText("Papai chegou em casa e contou uma historia gostosa."), List.of(fatherA, fatherB)))
                .isInstanceOfSatisfying(StoryNarrativeValidationException.class,
                        exception -> assertThat(exception.missingCharacters()).containsExactlyInAnyOrder("Papai Rafael", "Papai Ricardo"));
    }

    @Test
    void ambiguousAffectivePrefixStillValidatesTheOneNamedExplicitly() {
        StoryCharacterPrompt fatherA = character("Papai Rafael", null);
        StoryCharacterPrompt fatherB = character("Papai Ricardo", null);

        assertThatThrownBy(() -> validator.validateCharacters(
                storyWithText("Rafael chegou em casa e contou uma historia gostosa."), List.of(fatherA, fatherB)))
                .isInstanceOfSatisfying(StoryNarrativeValidationException.class,
                        exception -> assertThat(exception.missingCharacters()).containsExactly("Papai Ricardo"));
    }

    @Test
    void reportsExactlyTheMissingCharacterWhenOthersArePresent() {
        StoryCharacterPrompt present = character("Fernando", null);
        StoryCharacterPrompt missing = character("Thamires", null);

        assertThatThrownBy(() -> validator.validateCharacters(
                storyWithText("Fernando brincou no jardim sozinho o dia todo."), List.of(present, missing)))
                .isInstanceOfSatisfying(StoryNarrativeValidationException.class,
                        exception -> assertThat(exception.missingCharacters()).containsExactly("Thamires"));
    }

    @Test
    void protagonistMissingFailsValidation() {
        StoryCharacterPrompt protagonist = character("Fernando", null);

        assertThatThrownBy(() -> validator.validateCharacters(
                storyWithText("Uma aventura aconteceu na floresta certa noite."), List.of(protagonist)))
                .isInstanceOfSatisfying(StoryNarrativeValidationException.class,
                        exception -> {
                            assertThat(exception.reason()).isEqualTo("CHARACTER_MISSING");
                            assertThat(exception.missingCharacters()).containsExactly("Fernando");
                        });
    }

    @Test
    void noCharactersToValidateIsANoOp() {
        validator.validateCharacters(storyWithText("Qualquer coisa."), List.of());
        validator.validateCharacters(storyWithText("Qualquer coisa."), null);
    }

    private StoryCharacterPrompt character(String name, String nickname) {
        return new StoryCharacterPrompt(UUID.randomUUID(), name, nickname, null, 1, StoryCharacterRole.PROTAGONIST, "descricao visual");
    }

    private GeneratedStory storyWithText(String content) {
        return new GeneratedStory("Uma aventura", "Resumo da aventura.", List.of(new GeneratedChapter(1, "Capitulo unico", content)));
    }
}
