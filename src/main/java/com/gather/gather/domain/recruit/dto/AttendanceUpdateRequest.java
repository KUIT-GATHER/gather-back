package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.RecruitAttendanceStatus;
import jakarta.validation.constraints.NotNull;

/** 출석 처리 요청(#13). attendanceStatus는 PRESENT 또는 ABSENT만 허용한다(UNSET 지정 불가, 서비스에서 검증). */
public record AttendanceUpdateRequest(
        @NotNull(message = "출석 상태는 필수입니다.") RecruitAttendanceStatus attendanceStatus) {}
