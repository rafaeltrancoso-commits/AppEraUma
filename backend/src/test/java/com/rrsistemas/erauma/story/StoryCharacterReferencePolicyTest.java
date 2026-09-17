package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoryCharacterReferencePolicyTest {
    private final StoryCharacterReferencePolicy policy = new StoryCharacterReferencePolicy();

    @Test
    void adaptsKnownReferencesToStableOriginalIdentities() {
        var first = policy.normalize("Homem-Aranha, Superman e Pikachu");
        var second = policy.normalize("spider man; super-man; Pokémon");
        var spaced = policy.normalize("Super Man");

        assertThat(first.knownReferenceAdapted()).isTrue();
        assertThat(second.knownReferenceAdapted()).isTrue();
        assertThat(first.text()).isEqualTo(second.text())
                .contains("personagem original")
                .doesNotContainIgnoringCase("aranha")
                .doesNotContainIgnoringCase("superman")
                .doesNotContainIgnoringCase("pikachu");
        assertThat(spaced.knownReferenceAdapted()).isTrue();
        assertThat(spaced.text()).contains("personagem original").doesNotContainIgnoringCase("Super Man");
    }

    @Test
    void preservesFamilyRelationsAndFreeFictionalCharacters() {
        var normalized = policy.normalize("Vovó, Bolota e Capitão Inventado");

        assertThat(normalized.knownReferenceAdapted()).isFalse();
        assertThat(normalized.text()).isEqualTo("Vovó, Bolota, Capitão Inventado");
        assertThat(normalized.characters()).extracting(StoryCharacterReferencePolicy.AdditionalCharacter::kind)
                .containsExactly(
                        StoryCharacterReferencePolicy.AdditionalCharacterKind.FAMILIAR,
                        StoryCharacterReferencePolicy.AdditionalCharacterKind.FREE_FICTIONAL,
                        StoryCharacterReferencePolicy.AdditionalCharacterKind.FREE_FICTIONAL);
    }

    @Test
    void replacesKnownReferencesInsideNarrativeWithoutChangingOtherText() {
        var adapted = policy.adaptKnownReferencesInText("No parque, o Homem-Aranha ajudou Nando com a bicicleta.");

        assertThat(adapted.knownReferenceAdapted()).isTrue();
        assertThat(adapted.text()).contains("No parque", "Nando", "bicicleta", "personagem original")
                .doesNotContainIgnoringCase("Homem-Aranha");
    }
}
