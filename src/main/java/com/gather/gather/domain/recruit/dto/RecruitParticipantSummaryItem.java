package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitApplicantType;
import com.gather.gather.domain.recruit.entity.RecruitAttendanceStatus;
import java.time.LocalDateTime;

/** 신청자 목록 항목(#13). 개인정보(전화번호·생년월일 등)는 포함하지 않는다. */
public record RecruitParticipantSummaryItem(
        Long participationId,
        Long userId,
        String nickname,
        RecruitApplicantType applicantType,
        MeetingRecruitParticipationStatus participationStatus,
        RecruitAttendanceStatus attendanceStatus,
        LocalDateTime appliedAt) {}
