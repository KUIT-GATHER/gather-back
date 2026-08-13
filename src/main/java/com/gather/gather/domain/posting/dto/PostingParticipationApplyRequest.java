package com.gather.gather.domain.posting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 외부(1365/VMS) 신청 완료 후 사용자가 Gather에서 등록하는 개인 참여 일정.
 *
 * <p>단일 날짜를 선택한 경우에도 start/end에 동일한 날짜를 넣어 보낸다. actWkdy는 이번 정책에서 신청 날짜 검증에 사용하지 않는다 — 소스별(1365
 * raw string 저장 / VMS 미제공)로 정합성이 부족해 source of truth로 삼지 않기로 했다.
 */
@Schema(description = "봉사 신청 일정 등록 요청")
public record PostingParticipationApplyRequest(
        @Schema(description = "참여 일정 시작일 (단일 날짜 선택 시 종료일과 동일)", example = "2026-08-15")
                @NotNull LocalDate participationStartDate,
        @Schema(description = "참여 일정 종료일 (단일 날짜 선택 시 시작일과 동일)", example = "2026-08-18")
                @NotNull LocalDate participationEndDate) {}
