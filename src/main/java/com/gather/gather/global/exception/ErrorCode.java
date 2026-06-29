package com.gather.gather.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
    MEETING_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 모임입니다."),
    MEETING_FULL(HttpStatus.CONFLICT, "모임 인원이 가득 찼습니다."),
    MEETING_CLOSED(HttpStatus.CONFLICT, "마감된 모임입니다."),
    INVALID_MEETING_TIME(HttpStatus.BAD_REQUEST, "모임 시간이 올바르지 않습니다."),;

    private final HttpStatus status;
    private final String message;
}
