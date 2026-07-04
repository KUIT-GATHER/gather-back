package com.gather.gather.domain.auth.service;

import com.gather.gather.global.exception.ErrorCode;
import lombok.Getter;

/**
 * Access Token(JWT) 파싱/검증 실패를 나타내는 JWT 전용 예외.
 *
 * <p>JJWT 라이브러리 예외가 {@link TokenProvider} 밖으로 그대로 새어나가지 않도록 이 예외로 감싸며, 내부에 기존
 * ErrorCode({@link ErrorCode#INVALID_TOKEN} / {@link ErrorCode#EXPIRED_TOKEN})를 보유한다.
 */
@Getter
public class JwtAuthenticationException extends RuntimeException {

    private final ErrorCode errorCode;

    public JwtAuthenticationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public JwtAuthenticationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
