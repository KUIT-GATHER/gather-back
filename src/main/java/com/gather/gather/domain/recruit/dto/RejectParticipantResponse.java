package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitAttendanceStatus;
import java.time.LocalDateTime;

/** 신청자 반려 응답(#13). */
public record RejectParticipantResponse(
        Long participationId,
        MeetingRecruitParticipationStatus participationStatus,
        RecruitAttendanceStatus attendanceStatus,
        LocalDateTime updatedAt) {}
