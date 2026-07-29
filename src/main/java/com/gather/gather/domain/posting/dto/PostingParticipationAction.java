package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.PostingParticipationStatus;

/** 공고 상세 하단 버튼이 어떤 동작을 노출해야 하는지를 참여 상태로부터 파생한 값. */
public enum PostingParticipationAction {
    APPLY,
    CANCEL,
    COMPLETE,
    NONE;

    public static PostingParticipationAction from(PostingParticipationStatus status) {
        if (status == null) {
            return APPLY;
        }
        return switch (status) {
            case APPLIED -> CANCEL;
            case CONFIRMED -> COMPLETE;
            case COMPLETED, REVIEWED -> NONE;
        };
    }
}
