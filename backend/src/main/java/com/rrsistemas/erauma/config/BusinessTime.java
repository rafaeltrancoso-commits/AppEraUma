package com.rrsistemas.erauma.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Centraliza o calendario civil usado pelas regras de negocio do EraUma. */
@Component
public class BusinessTime {
    private final ZoneId zoneId;
    private final Clock clock;

    @Autowired
    public BusinessTime(@Value("${app.business-time-zone}") String configuredZoneId) {
        this(parseZone(configuredZoneId), null);
    }

    public BusinessTime(ZoneId zoneId, Clock clock) {
        this.zoneId = zoneId;
        this.clock = clock == null ? Clock.system(zoneId) : clock.withZone(zoneId);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public DayRange currentDay() {
        return day(today());
    }

    public DayRange day(LocalDate date) {
        Instant fromInclusive = date.atStartOfDay(zoneId).toInstant();
        Instant toExclusive = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        return new DayRange(fromInclusive, toExclusive);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    private static ZoneId parseZone(String value) {
        try {
            if (value == null || value.isBlank()) {
                throw new DateTimeException("empty time zone");
            }
            return ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid app.business-time-zone: " + value, exception);
        }
    }

    public record DayRange(Instant fromInclusive, Instant toExclusive) {}
}
