package com.workoutdone.rpgym.user.user.domain;

import com.workoutdone.rpgym.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    EMAIL_DUPLICATED(
            "EMAIL_DUPLICATED",
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),

    NICKNAME_DUPLICATED(
            "NICKNAME_DUPLICATED",
            HttpStatus.CONFLICT,
            "이미 사용 중인 닉네임입니다."
    ),

    // 이메일/비밀번호 불일치, 탈퇴한 계정, 존재하지 않는 계정을 모두 이 코드 하나로 응답
    LOGIN_FAILED(
            "LOGIN_FAILED",
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 일치하지 않습니다."
    ),

    ACCOUNT_SUSPENDED(
            "ACCOUNT_SUSPENDED",
            HttpStatus.FORBIDDEN,
            "정지된 계정입니다."
    ),

    // 존재하지 않음/만료/이미 폐기(로그아웃·재발급)됨
    // 발급 이후 탈퇴됨을 모두 이 코드 하나로 응답
    // (탈퇴 여부를 노출하지 않는 원칙은 LOGIN_FAILED와 동일)
    INVALID_REFRESH_TOKEN(
            "INVALID_REFRESH_TOKEN",
            HttpStatus.UNAUTHORIZED,
            "리프레시 토큰이 유효하지 않습니다. 다시 로그인해주세요."
    ),

    USER_NOT_FOUND(
            "USER_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "사용자를 찾을 수 없습니다."
    );

    private final String code;
    private final HttpStatus status;
    private final String message;
}
