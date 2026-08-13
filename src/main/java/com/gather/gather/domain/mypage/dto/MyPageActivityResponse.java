package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.posting.dto.PostingParticipationAction;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Schema(description = "마이페이지 활동 캘린더/다가오는 활동 일정카드")
public record MyPageActivityResponse(
        @Schema(description = "활동 종류(VOLUNTEER: 봉사공고 참여, MEETING_RECRUIT: 자유모임 내부 봉사공고 참여)")
        ActivityType activityType,
        @Schema(description = "참여(신청) ID", nullable = true, example = "1") Long participationId,
        @Schema(description = "봉사공고 ID (VOLUNTEER인 경우만 존재)", nullable = true, example = "10")
        Long postingId,
        @Schema(description = "모임 ID (MEETING_RECRUIT인 경우만 존재)", nullable = true, example = "15")
        Long meetingId,
        @Schema(description = "모집공고 게시글 ID (MEETING_RECRUIT인 경우만 존재)", nullable = true, example = "82")
        Long postId,
        @Schema(description = "제목", example = "함께하는 환경정화 봉사") String title,
        @Schema(
                description =
                        "활동 시작일. VOLUNTEER는 PostingParticipation.participationStartDate(없으면 공고"
                                + " 전체 시작일로 fallback), MEETING_RECRUIT은 MeetingRecruit.activityStartAt 날짜부분",
                example = "2026-08-15")
        LocalDate actStartDate,
        @Schema(description = "활동 종료일", nullable = true, example = "2026-08-18") LocalDate actEndDate,
        @Schema(description = "활동 시작 시각", nullable = true, example = "09:00") String actStartTime,
        @Schema(description = "활동 종료 시각", nullable = true, example = "12:00") String actEndTime,
        @Schema(description = "활동 장소", nullable = true, example = "서울숲공원") String actPlace,
        @Schema(description = "지역명", nullable = true, example = "강남구") String regionName,
        @Schema(
                description =
                        "참여 상태. VOLUNTEER는 APPLIED/CONFIRMED/COMPLETED/REVIEWED,"
                                + " MEETING_RECRUIT은 MeetingRecruitParticipationStatus 값",
                example = "APPLIED")
        String status,
        @Schema(
                description =
                        "카드에서 노출할 액션. MyPage에는 이미 생성된 참여만 반환되므로 실질적으로 CANCEL/NONE만 온다.",
                example = "CANCEL")
        String participationAction) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 일반 1365/VMS 봉사 참여 일정. 2026-08 정책 변경: 날짜 source가 공고 전체 활동기간에서 사용자 개인
     * {@code PostingParticipation.participationStartDate/EndDate}로 바뀌었다. 개인 일정이 없는(정책 변경 이전) 기존
     * 데이터만 공고 전체 활동기간으로 fallback한다 — 신규 정책 적용 이후 생성되는 참여는 항상 개인 일정을 가지므로 fallback 경로를 타지 않는다.
     */
    public static MyPageActivityResponse ofVolunteer(
            PostingParticipation participation, Posting posting) {
        LocalDate startDate =
                participation.getParticipationStartDate() != null
                        ? participation.getParticipationStartDate()
                        : posting.getActStartDate();
        LocalDate endDate =
                participation.getParticipationEndDate() != null
                        ? participation.getParticipationEndDate()
                        : posting.getActEndDate();

        boolean activityEnded =
                PostingParticipationAction.resolveActivityEnded(
                        posting, participation.getParticipationEndDate(), LocalDate.now());
        PostingParticipationAction action =
                PostingParticipationAction.from(participation.getStatus(), activityEnded);

        return new MyPageActivityResponse(
                ActivityType.VOLUNTEER,
                participation.getId(),
                posting.getId(),
                null,
                null,
                posting.getTitle(),
                startDate,
                endDate,
                posting.getActStartTime(),
                posting.getActEndTime(),
                posting.getActPlace(),
                null,
                participation.getStatus().name(),
                action.name());
    }

    /**
     * 자유모임 내부 {@code MeetingRecruit} 봉사공고 참여 일정. 일정 source는 {@code
     * MeetingRecruit.activityStartAt/activityEndAt}(별도 필드를 새로 만들지 않고 기존 필드를 공통 응답 필드로 변환).
     *
     * @param participationAction "CANCEL" 또는 "NONE" — MeetingRecruit 상세 API의 기존 취소 가능 정책과 동일 기준으로
     *     호출부(MyPageService)에서 계산해 전달한다.
     */
    public static MyPageActivityResponse ofMeetingRecruit(
            Long participationId,
            Long meetingId,
            Long postId,
            String title,
            String place,
            String regionName,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt,
            String status,
            String participationAction) {
        return new MyPageActivityResponse(
                ActivityType.MEETING_RECRUIT,
                participationId,
                null,
                meetingId,
                postId,
                title,
                activityStartAt.toLocalDate(),
                activityEndAt.toLocalDate(),
                TIME_FORMAT.format(activityStartAt),
                TIME_FORMAT.format(activityEndAt),
                place,
                regionName,
                status,
                participationAction);
    }

    public enum ActivityType {
        VOLUNTEER,
        MEETING_RECRUIT
    }
}
