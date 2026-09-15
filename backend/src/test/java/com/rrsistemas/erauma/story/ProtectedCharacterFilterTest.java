package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProtectedCharacterFilterTest {

    @Test
    void detectsKnownFranchiseCharactersRegardlessOfAccentsAndCase() {
        assertThat(ProtectedCharacterFilter.containsProtectedReference("Homem Aranha")).isTrue();
        assertThat(ProtectedCharacterFilter.containsProtectedReference("homem aranha")).isTrue();
        assertThat(ProtectedCharacterFilter.containsProtectedReference("HOMEM ARANHA")).isTrue();
        assertThat(ProtectedCharacterFilter.containsProtectedReference("uma amiga chamada Elsa da Frozen")).isTrue();
        assertThat(ProtectedCharacterFilter.containsProtectedReference("adora o Batman")).isTrue();
    }

    @Test
    void doesNotFlagUnrelatedFreeText() {
        assertThat(ProtectedCharacterFilter.containsProtectedReference("Luna")).isFalse();
        assertThat(ProtectedCharacterFilter.containsProtectedReference("Aprendendo judo")).isFalse();
        assertThat(ProtectedCharacterFilter.containsProtectedReference("Uma pintinha no queixo")).isFalse();
        assertThat(ProtectedCharacterFilter.containsProtectedReference(null)).isFalse();
        assertThat(ProtectedCharacterFilter.containsProtectedReference("")).isFalse();
    }

    @Test
    void sanitizeReturnsFallbackOnlyWhenFlagged() {
        assertThat(ProtectedCharacterFilter.sanitize("Homem Aranha", "amigo generico")).isEqualTo("amigo generico");
        assertThat(ProtectedCharacterFilter.sanitize("Luna", "amigo generico")).isEqualTo("Luna");
    }
}
