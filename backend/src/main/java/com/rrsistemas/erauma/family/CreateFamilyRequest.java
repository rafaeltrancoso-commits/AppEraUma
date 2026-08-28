package com.rrsistemas.erauma.family;

import jakarta.validation.constraints.NotBlank;

public record CreateFamilyRequest(@NotBlank String name) {}

