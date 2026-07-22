package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record MeetingCreateRequest(
        @NotBlank(message = "모임 이름은 필수입니다.") @Size(max = 100, message = "모임 이름은 100자 이하여야 합니다.")
                String name,
        String description,
        @NotNull(message = "최대 인원은 필수입니다.") @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
                Integer maxMember,
        @NotNull(message = "신청 마감일은 필수입니다.") LocalDateTime deadline,
        String memo,
        @Schema(
                        description =
                                "카테고리. 자유 모임 생성 시 필수이며, 공고 기반 모임은 volunteerPostingId에 해당하는 봉사공고의 category를 사용합니다.",
                        example = "WELFARE")
                PostingCategory category,
        @Schema(description = "지역 ID", example = "1") @NotNull(message = "지역은 필수입니다.")
                Long regionId,
        String participationCondition,
        @Schema(description = "공고 기반 모임 생성 시 연결할 봉사공고 ID. 자유 모임이면 null", example = "10")
                Long volunteerPostingId,
        @NotNull(message = "활동 시작 시간은 필수입니다.") LocalDateTime activityStartAt,
        @NotNull(message = "활동 종료 시간은 필수입니다.") LocalDateTime activityEndAt) {}
