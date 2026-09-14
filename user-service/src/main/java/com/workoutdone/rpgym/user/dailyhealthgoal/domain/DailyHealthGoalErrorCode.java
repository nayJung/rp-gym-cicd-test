package com.workoutdone.rpgym.user.dailyhealthgoal.domain;

import com.workoutdone.rpgym.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DailyHealthGoalErrorCode implements ErrorCode {

    DAILY_GOAL_ALREADY_EXISTS(
            "DAILY_GOAL_ALREADY_EXISTS",
            HttpStatus.CONFLICT,
            "이미 등록된 일일 목표가 있습니다."
    ),

    DAILY_GOAL_NOT_FOUND(
            "DAILY_GOAL_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "등록된 일일 목표가 없습니다."
    );

    private final String code;
    private final HttpStatus status;
    private final String message;
}
