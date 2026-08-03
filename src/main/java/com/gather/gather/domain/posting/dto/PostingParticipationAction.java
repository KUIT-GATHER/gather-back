package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.PostingParticipationStatus;

/**
 * 공고 상세 하단 버튼이 어떤 동작을 노출해야 하는지를 참여 상태와 활동종료 여부로부터 파생한 값.
 *
 * <p>개인 봉사는 활동종료일이 지나야 완료 처리를 할 수 있으므로(신청 직후엔 취소만 가능), 상태만으로는 버튼을 결정할 수 없다.
 */
public enum PostingParticipationAction {
    APPLY,
    CANCEL,
    COMPLETE,
    NONE;

    public static PostingParticipationAction from(
            PostingParticipationStatus status, boolean activityEnded) {
        if (status == null) {
            return APPLY;
        }
        return switch (status) {
            case APPLIED, CONFIRMED -> activityEnded ? COMPLETE : CANCEL;
            case COMPLETED, REVIEWED -> NONE;
        };
    }
}
