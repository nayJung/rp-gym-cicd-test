package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 파티 도메인 에러. 공통 것(INVALID_INPUT · UNAUTHORIZED)은 CommonErrorCode 를 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum PartyErrorCode implements ErrorCode {

    PARTY_NOT_FOUND("PARTY_NOT_FOUND", HttpStatus.NOT_FOUND, "파티를 찾을 수 없습니다."),
    INVITATION_NOT_FOUND("INVITATION_NOT_FOUND", HttpStatus.NOT_FOUND, "초대를 찾을 수 없습니다."),
    NOT_IN_PARTY("NOT_IN_PARTY", HttpStatus.NOT_FOUND, "소속된 파티가 없습니다."),

    ALREADY_IN_PARTY("ALREADY_IN_PARTY", HttpStatus.CONFLICT, "이미 다른 파티에 소속되어 있습니다."),
    NOT_PARTY_MEMBER("NOT_PARTY_MEMBER", HttpStatus.FORBIDDEN, "파티 멤버가 아닙니다."),
    NOT_PARTY_OWNER("NOT_PARTY_OWNER", HttpStatus.FORBIDDEN, "파티장만 할 수 있습니다."),
    PARTY_NOT_RECRUITING("PARTY_NOT_RECRUITING", HttpStatus.CONFLICT, "모집 중인 파티가 아닙니다."),
    PARTY_FULL("PARTY_FULL", HttpStatus.CONFLICT, "파티 정원이 가득 찼습니다."),
    INVITATION_NOT_PENDING("INVITATION_NOT_PENDING", HttpStatus.CONFLICT, "이미 처리됐거나 만료된 초대입니다."),
    NOT_INVITEE("NOT_INVITEE", HttpStatus.FORBIDDEN, "초대받은 사용자가 아닙니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
