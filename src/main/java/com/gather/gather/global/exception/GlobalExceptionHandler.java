package com.gather.gather.global.exception;

import com.gather.gather.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.name(), errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        return validationErrorResponse();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        return validationErrorResponse();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("처리되지 않은 예외 발생", exception);
        log.error("처리되지 않은 예외가 발생했습니다.", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(
                        ApiResponse.error(
                                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> validationErrorResponse() {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(
                        ApiResponse.error(
                                ErrorCode.VALIDATION_ERROR.name(),
                                ErrorCode.VALIDATION_ERROR.getMessage()));
    }
}
