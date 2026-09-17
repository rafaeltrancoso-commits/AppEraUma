package com.rrsistemas.erauma.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BusinessTimeTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void usesSaoPauloCivilDayInsteadOfUtcNearMidnight() {
        BusinessTime beforeLocalMidnight = fixedAt("2026-01-01T02:59:59Z");
        assertThat(beforeLocalMidnight.today()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(beforeLocalMidnight.currentDay().fromInclusive()).isEqualTo(Instant.parse("2025-12-31T03:00:00Z"));
        assertThat(beforeLocalMidnight.currentDay().toExclusive()).isEqualTo(Instant.parse("2026-01-01T03:00:00Z"));

        BusinessTime atLocalMidnight = fixedAt("2026-01-01T03:00:00Z");
        assertThat(atLocalMidnight.today()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(atLocalMidnight.currentDay().fromInclusive()).isEqualTo(Instant.parse("2026-01-01T03:00:00Z"));
        assertThat(atLocalMidnight.currentDay().toExclusive()).isEqualTo(Instant.parse("2026-01-02T03:00:00Z"));
    }

    @Test
    void producesInclusiveStartAndExclusiveNextDayForRequestedDate() {
        BusinessTime businessTime = fixedAt("2026-06-15T12:00:00Z");
        BusinessTime.DayRange range = businessTime.day(LocalDate.of(2026, 6, 15));
        assertThat(range.fromInclusive()).isEqualTo(Instant.parse("2026-06-15T03:00:00Z"));
        assertThat(range.toExclusive()).isEqualTo(Instant.parse("2026-06-16T03:00:00Z"));
    }

    @Test
    void rejectsInvalidConfiguredZoneDuringConstruction() {
        assertThatThrownBy(() -> new BusinessTime("Brazil/Nowhere"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid app.business-time-zone");
    }

    private BusinessTime fixedAt(String instant) {
        return new BusinessTime(SAO_PAULO, Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")));
    }
}
