package com.gather.gather.domain.meeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MeetingRecognizedMinutesRequest(
        @Schema(description = "봉사 인정시간(분 단위, 10분 단위로만 입력 가능)", example = "210")
                Integer recognizedMinutes) {}
