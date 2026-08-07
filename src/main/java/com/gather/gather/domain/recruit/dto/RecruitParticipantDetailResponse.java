package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitApplicantType;
import com.gather.gather.domain.recruit.entity.RecruitAttendanceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 신청자 상세 조회 응답(#13, 팀장 전용). */
public record RecruitParticipantDetailResponse(
        Long participationId,
        Long userId,
        String nickname,
        RecruitApplicantType applicantType,
        MeetingRecruitParticipationStatus participationStatus,
        RecruitAttendanceStatus attendanceStatus,
        String phoneNumber,
        LocalDate birthDate,
        Long regionId,
        String regionName,
        List<PostingCategory> interestCategories,
        int totalRecognizedMinutes,
        LocalDateTime appliedAt) {}
