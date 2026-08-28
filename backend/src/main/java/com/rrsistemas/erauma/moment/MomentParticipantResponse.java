package com.rrsistemas.erauma.moment;

import java.util.UUID;

public record MomentParticipantResponse(UUID id, String name, ParticipantType participantType) {
    public static MomentParticipantResponse from(MomentParticipant participant) {
        return new MomentParticipantResponse(participant.getId(), participant.getName(), participant.getParticipantType());
    }
}
