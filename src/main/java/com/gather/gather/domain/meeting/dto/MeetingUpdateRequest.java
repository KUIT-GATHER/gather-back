package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 모임 기본 정보 수정 요청(PATCH /api/v1/meetings/{meetingId}).
 *
 * <p>categories·regionId는 자유 모임에서만 실제로 반영된다. 공고 기반 모임은 연결된 봉사공고 기준으로 지역·카테고리가 고정되어 있어, 값을 전달해도 무시되고
 * 기존 값이 유지된다.
 */
public record MeetingUpdateRequest(
        @NotBlank(message = "모임 이름은 필수입니다.") @Size(max = 100, message = "모임 이름은 100자 이하여야 합니다.")
                String name,
        String description,
        @NotNull(message = "최대 인원은 필수입니다.") @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
                Integer maxMember,
        @NotNull(message = "신청 마감일은 필수입니다.") LocalDateTime deadline,
        @Schema(
                        description = "모임 카테고리. 자유 모임은 1~3개를 전달합니다. 공고 기반 모임은 무시되며 기존 값을 유지합니다.",
                        example = "[\"ENVIRONMENT\", \"EDUCATION\"]",
                        nullable = true)
                @Size(max = 3, message = "카테고리는 최대 3개까지 선택할 수 있습니다.")
                Set<@NotNull(message = "카테고리 값은 null일 수 없습니다.") PostingCategory> categories,
        String participationCondition,
        @Schema(
                        description = "지역 ID. 자유 모임만 수정 가능하며, 공고 기반 모임에서는 무시됩니다.",
                        example = "1",
                        nullable = true)
                Long regionId,
        @Schema(description = "봉사시간 인정 여부. 공고 기반 모임에서만 반영되며, 자유 모임은 무시됩니다.")
                boolean timeRecognized) {}
