package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitAttendanceStatus;
import java.time.LocalDateTime;

/** 출석 처리 응답(#13). */
public record UpdateAttendanceResponse(
        Long participationId,
        MeetingRecruitParticipationStatus participationStatus,
        RecruitAttendanceStatus attendanceStatus,
        int recognizedMinutesApplied,
        LocalDateTime updatedAt) {}
