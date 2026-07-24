package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.PostingParticipationStatus;

public record PostingParticipationResponse(
        Long participationId, PostingParticipationStatus status, String applicationUrl) {

    public static PostingParticipationResponse of(
            Long participationId, PostingParticipationStatus status, String applicationUrl) {
        return new PostingParticipationResponse(participationId, status, applicationUrl);
    }
}
