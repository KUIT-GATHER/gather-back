package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/** 모집공고(RECRUIT) 수정 요청. 작성한 팀장 본인만 수정할 수 있다. 필드 구성은 작성 요청과 동일하다. */
public record RecruitUpdateRequest(
        @Schema(description = "활동 제목")
                @NotBlank(message = "활동 제목은 필수입니다.")
                @Size(max = 15, message = "활동 제목은 15자 이내여야 합니다.")
                String title,
        @Schema(description = "활동 소개")
                @NotBlank(message = "활동 소개는 필수입니다.")
                @Size(max = 1000, message = "활동 소개는 1000자 이내여야 합니다.")
                String content,
        @Schema(description = "활동 장소") @NotBlank(message = "장소는 필수입니다.") @Size(max = 255) String place,
        @Schema(description = "활동 날짜") @NotNull(message = "활동 날짜는 필수입니다.") LocalDate actDate,
        @Schema(description = "활동 시작 시간(선택)") LocalTime actStartTime,
        @Schema(description = "활동 종료 시간(선택)") LocalTime actEndTime,
        @Schema(description = "최대 인원(최대 50)")
                @NotNull(message = "최대 인원은 필수입니다.")
                @Min(value = 1, message = "최대 인원은 1 이상이어야 합니다.")
                @Max(value = 50, message = "최대 인원은 50 이하여야 합니다.")
                Integer maxParticipants,
        @Schema(description = "카테고리(1~3개)")
                @NotEmpty(message = "카테고리는 1개 이상이어야 합니다.")
                @Size(max = 3, message = "카테고리는 최대 3개까지 선택할 수 있습니다.")
                Set<PostingCategory> categories,
        @Schema(description = "봉사시간 인정 여부") boolean timeRecognized,
        @Schema(description = "인정 시간(분). timeRecognized=true일 때 필수") @Positive Integer recognizedMinutes,
        @Schema(description = "신청 마감일") @NotNull(message = "신청 마감일은 필수입니다.") LocalDate applyDeadline,
        @Schema(description = "외부 공고 공개 여부") boolean isExternal) {}
