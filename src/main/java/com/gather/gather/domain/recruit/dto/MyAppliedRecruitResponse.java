package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 나의 활동 - 내가 신청한 봉사 카드. JPQL 생성자 표현식으로 채워지므로 컴포넌트 순서/타입이 쿼리와 일치해야 한다.
 *
 * <p>현재 상태는 신청/취소만 다뤄 항상 {@code APPLIED}(신청 완료)이며, 봉사완료·후기작성 상태는 후속에 추가한다.
 */
public record MyAppliedRecruitResponse(
        @Schema(description = "모집공고 게시글 ID") Long postId,
        @Schema(description = "모임 ID") Long meetingId,
        @Schema(description = "활동 제목") String title,
        @Schema(description = "활동 장소") String place,
        @Schema(description = "활동 날짜") LocalDate actDate,
        @Schema(description = "활동 시작 시간") LocalTime actStartTime,
        @Schema(description = "활동 종료 시간") LocalTime actEndTime,
        @Schema(description = "참여 상태") MeetingRecruitParticipationStatus status) {}
