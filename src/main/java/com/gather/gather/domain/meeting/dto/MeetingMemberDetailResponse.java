package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.util.List;

/** 모임원 상세(#11, 팀장 전용). */
public record MeetingMemberDetailResponse(
        Long userId,
        String nickname,
        MeetingMemberRole role,
        String phoneNumber,
        LocalDate birthDate,
        Long regionId,
        String regionName,
        List<PostingCategory> interestCategories,
        int totalRecognizedMinutes) {}
