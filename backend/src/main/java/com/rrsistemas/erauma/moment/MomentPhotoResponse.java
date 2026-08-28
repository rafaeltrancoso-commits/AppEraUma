package com.rrsistemas.erauma.moment;

import java.time.Instant;
import java.util.UUID;

public record MomentPhotoResponse(UUID id, String originalFilename, String contentType, long sizeBytes, int sortOrder, Instant createdAt) {
    public static MomentPhotoResponse from(MomentPhoto photo) {
        return new MomentPhotoResponse(photo.getId(), photo.getOriginalFilename(), photo.getContentType(), photo.getSizeBytes(), photo.getSortOrder(), photo.getCreatedAt());
    }
}
