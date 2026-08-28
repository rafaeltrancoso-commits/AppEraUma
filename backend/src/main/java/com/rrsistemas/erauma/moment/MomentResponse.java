package com.rrsistemas.erauma.moment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.rrsistemas.erauma.story.Story;
import com.rrsistemas.erauma.story.StoryImageResponse;

public record MomentResponse(
        UUID id,
        UUID familyId,
        String title,
        String description,
        LocalDateTime occurredAt,
        String locationName,
        boolean favorite,
        List<MomentChildResponse> children,
        List<MomentParticipantResponse> participants,
        List<MomentPhotoResponse> photos,
        List<MomentStoryResponse> stories,
        Instant createdAt,
        Instant updatedAt
) {
    public static MomentResponse from(Moment moment) {
        return from(moment, List.of());
    }

    public static MomentResponse from(Moment moment, List<Story> stories) {
        return new MomentResponse(
                moment.getId(),
                moment.getFamilyId(),
                moment.getTitle(),
                moment.getDescription(),
                moment.getOccurredAt(),
                moment.getLocationName(),
                moment.isFavorite(),
                moment.getChildren().stream().map(MomentChild::getChild).map(MomentChildResponse::from).toList(),
                moment.getParticipants().stream().map(MomentParticipantResponse::from).toList(),
                moment.getPhotos().stream().filter(MomentPhoto::isActive).sorted(Comparator.comparingInt(MomentPhoto::getSortOrder)).map(MomentPhotoResponse::from).toList(),
                stories.stream().map(story -> new MomentStoryResponse(story.getId(), story.getTitle(), story.getSummary(), story.getMainCharacterName(), story.getSecondCharacterName(), story.getTheme(), story.getCreatedAt(), story.getImages().stream().map(StoryImageResponse::from).toList())).toList(),
                moment.getCreatedAt(),
                moment.getUpdatedAt());
    }
}
