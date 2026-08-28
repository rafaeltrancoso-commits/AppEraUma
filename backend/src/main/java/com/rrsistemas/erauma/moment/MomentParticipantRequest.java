package com.rrsistemas.erauma.moment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MomentParticipantRequest(@NotBlank String name, @NotNull ParticipantType participantType) {}
