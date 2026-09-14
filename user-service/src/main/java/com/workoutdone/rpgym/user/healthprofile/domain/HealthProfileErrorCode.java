package com.workoutdone.rpgym.user.healthprofile.domain;

import com.workoutdone.rpgym.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HealthProfileErrorCode implements ErrorCode {

    HEALTH_PROFILE_ALREADY_EXISTS(
            "HEALTH_PROFILE_ALREADY_EXISTS",
            HttpStatus.CONFLICT,
            "이미 등록된 바디 프로필이 있습니다."
    ),

    HEALTH_PROFILE_NOT_FOUND(
            "HEALTH_PROFILE_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "등록된 바디 프로필이 없습니다."
    );

    private final String code;
    private final HttpStatus status;
    private final String message;
}
