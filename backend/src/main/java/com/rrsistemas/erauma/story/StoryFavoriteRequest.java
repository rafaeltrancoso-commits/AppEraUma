package com.rrsistemas.erauma.story;

import jakarta.validation.constraints.NotNull;

public record StoryFavoriteRequest(@NotNull Boolean favorite) {}
