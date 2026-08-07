package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 가입 신청자 상세(#10, 팀장 전용). */
public record MeetingJoinRequestDetailResponse(
        Long joinRequestId,
        Long userId,
        String nickname,
        MeetingMemberStatus status,
        LocalDateTime requestedAt,
        String phoneNumber,
        LocalDate birthDate,
        Long regionId,
        String regionName,
        List<PostingCategory> interestCategories,
        int totalRecognizedMinutes) {}
