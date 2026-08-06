package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 나의 활동 - 내가 신청한 봉사 카드. JPQL 생성자 표현식으로 채워지므로 컴포넌트 순서/타입이 쿼리와 일치해야 한다.
 *
 * <p>status는 {@code APPLIED}(신청) 또는 {@code COMPLETED}(봉사 완료) 중 하나로 내려온다. 모임장이 모임을 완료 처리하면 그 모임의
 * 모집공고 참여가 전부 {@code COMPLETED}로 일괄 전환된다. {@code CONFIRMED}·{@code REVIEWED}는 아직 이 상태로 전환하는 기능(참여
 * 승인·후기 작성)이 없어 현재는 내려오지 않는 예약값이다.
 */
public record MyAppliedRecruitResponse(
        @Schema(description = "모집공고 게시글 ID") Long postId,
        @Schema(description = "모임 ID") Long meetingId,
        @Schema(description = "활동 제목") String title,
        @Schema(description = "활동 장소") String place,
        @Schema(description = "활동 날짜") LocalDate actDate,
        @Schema(description = "활동 시작 시간") LocalTime actStartTime,
        @Schema(description = "활동 종료 시간") LocalTime actEndTime,
        @Schema(
                description =
                        "참여 상태(APPLIED/CONFIRMED/COMPLETED/REVIEWED). 현재는 APPLIED·COMPLETED만"
                                + " 실제로 내려오며, 화면에는 APPLIED/CONFIRMED를 \"신청중\", COMPLETED/REVIEWED를"
                                + " \"봉사 완료\"로 묶어 표시하는 것을 권장합니다.")
        MeetingRecruitParticipationStatus status) {}