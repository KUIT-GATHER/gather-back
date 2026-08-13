package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import java.time.LocalDateTime;

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