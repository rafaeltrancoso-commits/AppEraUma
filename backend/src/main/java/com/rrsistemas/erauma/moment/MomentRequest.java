package com.rrsistemas.erauma.moment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MomentRequest(
        @NotBlank String title,
        String description,
        @NotNull LocalDateTime occurredAt,
        String locationName,
        List<UUID> childIds,
        @Valid List<MomentParticipantRequest> participants
) {}
