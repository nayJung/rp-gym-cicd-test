package com.workoutdone.rpgym.game.quest.adapter.in.web;

import com.workoutdone.rpgym.common.exception.ErrorCode;
import com.workoutdone.rpgym.game.quest.application.SuggestionDecision;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 애플리케이션이 돌려준 판정 사유를 HTTP 응답으로 옮기는 표다.
//
// 이 변환을 어댑터에 둔 이유는 애플리케이션 계층이 HTTP 를 몰라야 하기 때문이다.
// 같은 판정이 나중에 다른 입구로도 쓰이면 그쪽은 그쪽대로 옮기면 된다.
//
// 공통 모듈의 ErrorCode 를 구현해서 응답 스키마와 코드 체계를 다른 API 와 맞춘다.
@Getter
@RequiredArgsConstructor
public enum SuggestionErrorCode implements ErrorCode {

    SUGGESTION_NOT_FOUND(
            "SUGGESTION_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            "제안을 찾을 수 없습니다."
    ),

    SUGGESTION_ALREADY_DECIDED(
            "SUGGESTION_ALREADY_DECIDED",
            HttpStatus.CONFLICT,
            "이미 처리된 제안입니다."
    ),

    SUGGESTION_EXPIRED(
            "SUGGESTION_EXPIRED",
            HttpStatus.GONE,
            "제안이 만료되었습니다. 다음 제안을 기다려 주세요."
    ),

    SUGGESTION_SUPERSEDED(
            "SUGGESTION_SUPERSEDED",
            HttpStatus.CONFLICT,
            "그 사이 건강 데이터가 갱신되어 이 제안은 더 이상 유효하지 않습니다."
    ),

    SUGGESTION_DATE_MISMATCH(
            "SUGGESTION_DATE_MISMATCH",
            HttpStatus.CONFLICT,
            "제안의 기준 날짜가 맞지 않습니다. 다음 제안을 기다려 주세요."
    ),

    QUEST_ALREADY_ACTIVE(
            "QUEST_ALREADY_ACTIVE",
            HttpStatus.CONFLICT,
            "이미 진행 중인 퀘스트가 있습니다. 끝낸 뒤에 다시 수락할 수 있습니다."
    ),

    // 계약이 깨진 상황이라 서버 쪽 문제에 가깝지만 5xx 로 내리지 않는다.
    // 5xx 는 부르는 쪽에게 다시 시도하라는 신호인데, 여기서는 다시 시도해도 결과가 같다.
    // 사람이 봐야 하는 문제라는 사실은 서비스 쪽에서 에러 로그로 이미 남기고 있다.
    SNAPSHOT_MISSING(
            "SNAPSHOT_MISSING",
            HttpStatus.CONFLICT,
            "건강 데이터가 아직 동기화되지 않아 퀘스트를 만들 수 없습니다."
    );

    private final String code;
    private final HttpStatus status;
    private final String message;

    public static SuggestionErrorCode of(SuggestionDecision.Reason reason) {
        return switch (reason) {
            case NOT_FOUND -> SUGGESTION_NOT_FOUND;
            case ALREADY_DECIDED -> SUGGESTION_ALREADY_DECIDED;
            case ALREADY_EXPIRED -> SUGGESTION_EXPIRED;
            case SUPERSEDED -> SUGGESTION_SUPERSEDED;
            case DATE_MISMATCH -> SUGGESTION_DATE_MISMATCH;
            case QUEST_ALREADY_ACTIVE -> SuggestionErrorCode.QUEST_ALREADY_ACTIVE;
            case SNAPSHOT_MISSING -> SuggestionErrorCode.SNAPSHOT_MISSING;
        };
    }
}
