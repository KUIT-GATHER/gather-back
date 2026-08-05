package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Schema(description = "마이페이지 활동 캘린더 일정카드")
public record MyPageActivityResponse(
        @Schema(description = "활동 종류(VOLUNTEER: 봉사공고 참여, MEETING: 모임 참여)", example = "VOLUNTEER")
                ActivityType activityType,
        @Schema(description = "참여(신청) ID (봉사공고 참여인 경우만 존재)", nullable = true, example = "1")
                Long participationId,
        @Schema(
                        description = "봉사공고 ID (상세보기 이동에 사용, 봉사공고 참여인 경우만 존재)",
                        nullable = true,
                        example = "10")
                Long postingId,
        @Schema(description = "모임 ID (상세보기 이동에 사용, 모임 참여인 경우만 존재)", nullable = true, example = "3")
                Long meetingId,
        @Schema(
                        description = "연결된 봉사공고 ID (공고 기반 모임인 경우만 존재, 자유 모임이거나 봉사 참여인 경우 null)",
                        nullable = true,
                        example = "10")
                Long volunteerPostingId,
        @Schema(description = "제목", example = "함께하는 환경정화 봉사") String title,
        @Schema(description = "활동 시작일", example = "2026-07-15") LocalDate actStartDate,
        @Schema(description = "활동 종료일", nullable = true, example = "2026-07-15")
                LocalDate actEndDate,
        @Schema(description = "활동 시작 시각", nullable = true, example = "09:00") String actStartTime,
        @Schema(description = "활동 종료 시각", nullable = true, example = "12:00") String actEndTime,
        @Schema(description = "활동 장소", nullable = true, example = "서울숲공원") String actPlace,
        @Schema(
                        description =
                                "봉사 참여 상태(APPLIED/CONFIRMED/COMPLETED/REVIEWED), 봉사공고 참여인 경우만 존재",
                        nullable = true,
                        example = "APPLIED")
                String status,
        @Schema(
                        description = "모임 모집 상태(RECRUITING/CLOSED/COMPLETED), 모임 참여인 경우만 존재",
                        nullable = true,
                        example = "RECRUITING")
                String meetingStatus,
        @Schema(
                        description =
                                "연결된 봉사공고에 대한 사용자의 참여 상태(APPLIED/CONFIRMED/COMPLETED/REVIEWED)."
                                        + " 공고 기반 모임인데 참여 이력이 없으면 null. 마이페이지 카드의 '신청중/봉사 완료'"
                                        + " 표시는 이 값을 기준으로 한다.",
                        nullable = true,
                        example = "APPLIED")
                String postingParticipationStatus) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static MyPageActivityResponse ofVolunteer(
            PostingParticipation participation, Posting posting) {
        return new MyPageActivityResponse(
                ActivityType.VOLUNTEER,
                participation.getId(),
                posting.getId(),
                null,
                null,
                posting.getTitle(),
                posting.getActStartDate(),
                posting.getActEndDate(),
                posting.getActStartTime(),
                posting.getActEndTime(),
                posting.getActPlace(),
                participation.getStatus().name(),
                null,
                null);
    }

    /**
     * @param linkedParticipationStatus 공고 기반 모임({@code meeting.getVolunteerPostingId() != null})일 때
     *     해당 사용자의 연결 봉사공고 참여 상태. 자유 모임이거나 참여 이력이 없으면 null.
     */
    public static MyPageActivityResponse ofMeeting(
            Meeting meeting, PostingParticipationStatus linkedParticipationStatus) {
        LocalDateTime start = meeting.getActivityStartAt();
        LocalDateTime end = meeting.getActivityEndAt();
        return new MyPageActivityResponse(
                ActivityType.MEETING,
                null,
                null,
                meeting.getId(),
                meeting.getVolunteerPostingId(),
                meeting.getName(),
                start.toLocalDate(),
                end != null ? end.toLocalDate() : null,
                TIME_FORMAT.format(start),
                end != null ? TIME_FORMAT.format(end) : null,
                null,
                null,
                meeting.getStatus().name(),
                linkedParticipationStatus != null ? linkedParticipationStatus.name() : null);
    }

    public enum ActivityType {
        VOLUNTEER,
        MEETING
    }
}
