package com.gather.gather.domain.meeting.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
public record MeetingCreateRequest(
        @NotBlank(message = "모임 이름은 필수입니다.")
        String name,
        String description,

        @NotNull(message = "최대 인원은 필수입니다.")
        @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
        Integer maxMember,

        @NotNull(message = "신청 마감일은 필수입니다.")
        LocalDateTime deadline,
        String memo,

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotNull(message = "지역은 필수입니다.")
        Long regionId,
        String participationCondition,
        Long volunteerPostingId,

        @NotNull(message = "활동 시작 시간은 필수입니다.")
        LocalDateTime activityStartAt,

        @NotNull(message = "활동 종료 시간은 필수입니다.")
        LocalDateTime activityEndAt) {}