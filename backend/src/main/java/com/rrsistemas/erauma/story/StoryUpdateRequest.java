package com.rrsistemas.erauma.story;

import jakarta.validation.constraints.NotBlank;

public record StoryUpdateRequest(@NotBlank String title, Boolean favorite) {}
