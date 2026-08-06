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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/** 모집공고(RECRUIT) 작성 요청. 팀장만 작성할 수 있다. 봉사시간 인정 시 recognizedMinutes는 서비스에서 필수 검증한다. */
public record RecruitCreateRequest(
        @Schema(description = "활동 제목", example = "6월 정기 활동 팀원 모집")
                @NotBlank(message = "활동 제목은 필수입니다.")
                @Size(max = 15, message = "활동 제목은 15자 이내여야 합니다.")
                String title,
        @Schema(description = "활동 소개")
                @NotBlank(message = "활동 소개는 필수입니다.")
                @Size(max = 1000, message = "활동 소개는 1000자 이내여야 합니다.")
                String content,
        @Schema(description = "활동 장소", example = "서울 영등포구 여의도동")
                @NotBlank(message = "장소는 필수입니다.")
                @Size(max = 255)
                String place,
        @Schema(description = "활동 날짜", example = "2026-05-20") @NotNull(message = "활동 날짜는 필수입니다.")
                LocalDate actDate,
        @Schema(description = "활동 시작 시간(선택)", example = "09:00") LocalTime actStartTime,
        @Schema(description = "활동 종료 시간(선택)", example = "12:00") LocalTime actEndTime,
        @Schema(description = "최대 인원(최대 50)", example = "30")
                @NotNull(message = "최대 인원은 필수입니다.")
                @Min(value = 1, message = "최대 인원은 1 이상이어야 합니다.")
                @Max(value = 50, message = "최대 인원은 50 이하여야 합니다.")
                Integer maxParticipants,
        @Schema(description = "카테고리(1~3개)")
                @NotEmpty(message = "카테고리는 1개 이상이어야 합니다.")
                @Size(max = 3, message = "카테고리는 최대 3개까지 선택할 수 있습니다.")
                Set<PostingCategory> categories,
        @Schema(description = "봉사시간 인정 여부") boolean timeRecognized,
        @Schema(description = "인정 시간(분). timeRecognized=true일 때 필수") @Positive
                Integer recognizedMinutes,
        @Schema(description = "신청 마감 일시", example = "2026-06-12T12:00:00")
                @NotNull(message = "신청 마감 일시는 필수입니다.")
                LocalDateTime applyDeadline,
        @Schema(description = "외부 공고 공개 여부(현재는 플래그만 저장)") boolean isExternal,
        @Schema(description = "참여 조건(선택)", example = "성인 및 청소년 단체 신청 가능")
                @Size(max = 255, message = "참여 조건은 255자 이내여야 합니다.")
                String participationCondition) {}
