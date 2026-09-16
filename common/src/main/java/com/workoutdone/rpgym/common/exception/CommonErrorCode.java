package com.workoutdone.rpgym.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(
            "INVALID_INPUT",
            HttpStatus.BAD_REQUEST,
            "입력값이 올바르지 않습니다."
    ),

    INVALID_REQUEST(
            "INVALID_REQUEST",
            HttpStatus.BAD_REQUEST,
            "잘못된 요청입니다."
    ),

    INVALID_SORT_FIELD(
            "INVALID_SORT_FIELD",
            HttpStatus.BAD_REQUEST,
            "정렬 필드가 올바르지 않습니다."
    ),

    METHOD_NOT_ALLOWED(
            "METHOD_NOT_ALLOWED",
            HttpStatus.METHOD_NOT_ALLOWED,
            "지원하지 않는 HTTP 메서드입니다."
    ),

    UNSUPPORTED_MEDIA_TYPE(
            "UNSUPPORTED_MEDIA_TYPE",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "지원하지 않는 Content-Type입니다."
    ),

    UNAUTHORIZED(
            "UNAUTHORIZED",
            HttpStatus.UNAUTHORIZED,
            "인증이 필요합니다."
    ),

    FORBIDDEN(
            "FORBIDDEN",
            HttpStatus.FORBIDDEN,
            "접근 권한이 없습니다."
    ),

    RESOURCE_NOT_FOUND(
            "RESOURCE_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "리소스를 찾을 수 없습니다."
    ),

    // 낙관적 락(@Version) 충돌
    CONFLICT(
            "CONFLICT",
            HttpStatus.CONFLICT,
            "다른 요청에 의해 데이터가 변경되었습니다. 다시 시도해주세요."
    ),

    SERVICE_UNAVAILABLE(
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            "서비스를 사용할 수 없습니다."
    ),

    INTERNAL_ERROR(
            "INTERNAL_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 오류가 발생했습니다."
    );

    private final String code;
    private final HttpStatus status;
    private final String message;
}