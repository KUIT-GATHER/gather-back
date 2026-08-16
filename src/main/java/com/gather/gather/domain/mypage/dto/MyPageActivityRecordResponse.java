package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "활동기록 상세 - 봉사 카드")
public record MyPageActivityRecordResponse(
        @Schema(description = "참여(신청) ID", example = "1") Long participationId,
        @Schema(description = "봉사공고 ID", example = "10") Long postingId,
        @Schema(description = "봉사공고 제목", example = "함께하는 환경정화 봉사") String title,
        @Schema(description = "봉사 분야(카드 테두리 색 결정에 사용)") PostingCategory category,
        @Schema(description = "활동 시작일", nullable = true, example = "2026-07-15")
                LocalDate actStartDate,
        @Schema(description = "활동 종료일", nullable = true, example = "2026-07-15")
                LocalDate actEndDate,
        @Schema(description = "활동 장소", nullable = true, example = "서울숲공원") String actPlace,
        @Schema(description = "인정시간(분 단위, 미입력 시 null)", nullable = true, example = "120")
                Integer recognizedMinutes,
        @Schema(description = "시간 인증형 활동 여부(인정시간이 입력되어 있으면 true)", example = "true")
                boolean timeCertifiable) {

    public static MyPageActivityRecordResponse of(
            PostingParticipation participation, Posting posting) {
        // 2026-08 정책: 활동기록 날짜도 다가오는 활동과 동일하게 개인 참여 일정 기준.
        // 개인 일정이 없는(정책 변경 이전) 기존 데이터만 공고 전체 활동기간으로 fallback한다.
        LocalDate actStartDate =
                participation.getParticipationStartDate() != null
                        ? participation.getParticipationStartDate()
                        : posting.getActStartDate();
        LocalDate actEndDate =
                participation.getParticipationEndDate() != null
                        ? participation.getParticipationEndDate()
                        : posting.getActEndDate();

        return new MyPageActivityRecordResponse(
                participation.getId(),
                posting.getId(),
                posting.getTitle(),
                posting.getCategory(),
                actStartDate,
                actEndDate,
                posting.getActPlace(),
                participation.getRecognizedMinutes(),
                participation.getRecognizedMinutes() != null);
    }
}
