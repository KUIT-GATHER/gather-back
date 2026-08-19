package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import java.time.LocalDateTime;

/**
 * 마이페이지 조회용 - 자유모임 내부 {@code MeetingRecruit}에 대한 사용자의 참여 일정 프로젝션.
 *
 * <p>{@code MeetingRecruitParticipationRepository.findMyUpcomingSchedules(...)}의 반환 타입. 취소 가능 여부는
 * {@code MeetingRecruit} 상세 API와 동일 기준(마감 전 + 미확정)으로 {@code MyPageService}에서 계산한다.
 */
public record MyPageMeetingRecruitSchedule(
        Long participationId,
        MeetingRecruitParticipationStatus participationStatus,
        Long meetingId,
        Long postId,
        String title,
        Long regionId,
        String place,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime applyDeadlineAt,
        RecruitConfirmationStatus confirmationStatus) {}
