package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import java.time.LocalDate;

/**
 * 공고 상세/마이페이지 하단(또는 카드) 버튼이 어떤 동작을 노출해야 하는지를 참여 상태와 활동종료 여부로부터 파생한 값.
 *
 * <p>개인 봉사는 활동종료일이 지나야 완료 처리를 할 수 있으므로(신청 직후엔 취소만 가능), 상태만으로는 버튼을 결정할 수 없다.
 *
 * <p>2026-08 정책 변경: 활동종료 여부는 더 이상 공고 전체 {@code Posting.actEndDate}가 아니라 사용자 개인 {@code
 * PostingParticipation.participationEndDate} 기준으로 계산한다. 개인 일정이 없는(정책 변경 이전) 기존 참여만 공고 전체 종료일로
 * fallback한다.
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

    /**
     * 개인 참여 일정(participationEndDate)을 우선 기준으로 활동종료 여부를 계산한다. participationEndDate가 없는 기존 데이터는
     * {@link Posting#isActivityEnded(LocalDate)}로 fallback한다.
     *
     * <p>공고 상세({@code PostingResponse}), 완료 처리({@code PostingParticipationService.complete}), 마이페이지
     * 조회 3곳 모두 이 메서드 하나로 판정 기준을 통일한다 — 서로 다른 기준을 쓰면 "상세에서는 완료 가능인데 마이페이지에서는 아니다" 같은 불일치가 생긴다.
     */
    public static boolean resolveActivityEnded(
            Posting posting, LocalDate participationEndDate, LocalDate today) {
        if (participationEndDate != null) {
            return !participationEndDate.isAfter(today);
        }
        return posting.isActivityEnded(today);
    }
}
