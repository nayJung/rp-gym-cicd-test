package com.workoutdone.rpgym.game.quest.adapter.in.web;

import com.workoutdone.rpgym.common.exception.ErrorCode;
import com.workoutdone.rpgym.game.quest.application.PartyQuestCreation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 파티 퀘스트 생성 실패 사유를 HTTP 응답으로 옮기는 표다.
// 변환을 어댑터에 두는 이유는 애플리케이션 계층이 HTTP 를 몰라야 하기 때문이다.
@Getter
@RequiredArgsConstructor
public enum PartyQuestErrorCode implements ErrorCode {

    INVALID_PARTY_MEMBERS(
            "INVALID_PARTY_MEMBERS",
            HttpStatus.BAD_REQUEST,
            "참여 인원이 올바르지 않습니다. 1명 이상 4명 이하로, 중복 없이 보내주세요."
    ),

    NOT_A_PARTY_MEMBER(
            "NOT_A_PARTY_MEMBER",
            HttpStatus.FORBIDDEN,
            "본인이 참여하지 않는 파티의 퀘스트는 만들 수 없습니다."
    ),

    INVALID_METRIC(
            "INVALID_METRIC",
            HttpStatus.BAD_REQUEST,
            "지표가 올바르지 않습니다. STEPS, ACTIVE_MINUTES, ACTIVE_CALORIES 중 하나여야 합니다."
    ),

    INVALID_TARGET_VALUE(
            "INVALID_TARGET_VALUE",
            HttpStatus.BAD_REQUEST,
            "목표값은 1 이상이어야 합니다."
    ),

    INVALID_TITLE(
            "INVALID_TITLE",
            HttpStatus.BAD_REQUEST,
            "제목은 비어 있을 수 없고 100자를 넘을 수 없습니다."
    ),

    PARTY_QUEST_ALREADY_ACTIVE(
            "PARTY_QUEST_ALREADY_ACTIVE",
            HttpStatus.CONFLICT,
            "이미 진행 중인 파티 퀘스트가 있습니다. 끝난 뒤에 새로 만들 수 있습니다."
    ),

    // 400 이 아니라 409 인 이유는 요청 자체는 올바르기 때문이다.
    // 지금 시점에 만들 수 없다는 뜻이라 자정이 지나면 같은 요청이 그대로 성공한다.
    TOO_LATE_IN_DAY(
            "TOO_LATE_IN_DAY",
            HttpStatus.CONFLICT,
            "오늘 남은 시간이 없습니다. 파티 퀘스트는 당일 자정까지라 자정 이후에 만들어 주세요."
    );

    private final String code;
    private final HttpStatus status;
    private final String message;

    public static PartyQuestErrorCode of(PartyQuestCreation.Reason reason) {
        return switch (reason) {
            case INVALID_MEMBERS -> INVALID_PARTY_MEMBERS;
            case NOT_A_MEMBER -> NOT_A_PARTY_MEMBER;
            case UNKNOWN_METRIC -> INVALID_METRIC;
            case INVALID_TARGET -> INVALID_TARGET_VALUE;
            case INVALID_TITLE -> PartyQuestErrorCode.INVALID_TITLE;
            case PARTY_QUEST_ALREADY_ACTIVE -> PartyQuestErrorCode.PARTY_QUEST_ALREADY_ACTIVE;
            case TOO_LATE_IN_DAY -> PartyQuestErrorCode.TOO_LATE_IN_DAY;
        };
    }
}
