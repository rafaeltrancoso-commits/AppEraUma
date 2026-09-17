package com.rrsistemas.erauma.story;

public enum AiImageFailureReason {
    MODERATION_BLOCKED,
    RATE_LIMIT,
    AUTHENTICATION,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    INVALID_RESPONSE,
    STORAGE_FAILURE,
    UNKNOWN
}
