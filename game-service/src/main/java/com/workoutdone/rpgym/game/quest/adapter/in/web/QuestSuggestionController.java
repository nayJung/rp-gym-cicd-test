package com.workoutdone.rpgym.game.quest.adapter.in.web;

import com.workoutdone.rpgym.common.response.ErrorResponse;
import com.workoutdone.rpgym.game.quest.adapter.in.web.dto.QuestResponse;
import com.workoutdone.rpgym.game.quest.application.QuestSuggestionAcceptService;
import com.workoutdone.rpgym.game.quest.application.SuggestionDecision;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 유저가 Slack 카드에서 수락이나 거절을 누르면 알림 담당 서비스가 여기로 HTTP 를 보낸다.
// 인증 주체는 게이트웨이가 넣어주는 X-User-Id 헤더에서 얻는다. 다른 컨트롤러와 같다.
// 헤더가 없으면 예외 처리기가 401 로 만든다. 게이트웨이를 거쳤다면 반드시 있어야 하는 값이다.
// 여기서 눈여겨볼 것은 실패를 예외로 만들지 않는다는 점이다.
// 근거 스냅샷이 낡았을 때는 제안을 무효 상태로 바꾸면서 동시에 실패를 알려야 하는데,
// 예외를 던지면 트랜잭션이 롤백되면서 그 상태 변경이 통째로 사라진다.
// 그래서 서비스가 결과를 값으로 돌려주고, 그 값을 상태 코드로 옮기는 일만 여기서 한다.
@RestController
@RequestMapping("/api/v1/games/quest-suggestions")
@RequiredArgsConstructor
public class QuestSuggestionController {

    private final QuestSuggestionAcceptService questSuggestionAcceptService;

    // 수락하면 퀘스트가 만들어지므로 201 이다.
    // 본문으로 만들어진 퀘스트를 돌려주는데, 진행 중인 퀘스트 조회 API 와 같은 모양이다.
    @PostMapping("/{suggestionId}/accept")
    public ResponseEntity<Object> accept(
            @PathVariable UUID suggestionId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return toResponse(questSuggestionAcceptService.accept(suggestionId, userId));
    }

    // 거절은 제안을 닫기만 하고 만들어지는 것이 없어서 본문이 없다. 그래서 204 다.
    @PostMapping("/{suggestionId}/reject")
    public ResponseEntity<Object> reject(
            @PathVariable UUID suggestionId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return toResponse(questSuggestionAcceptService.reject(suggestionId, userId));
    }

    private ResponseEntity<Object> toResponse(SuggestionDecision decision) {
        if (decision instanceof SuggestionDecision.Accepted accepted) {
            return ResponseEntity.status(HttpStatus.CREATED).body(QuestResponse.from(accepted.quest()));
        }
        if (decision instanceof SuggestionDecision.Rejected) {
            return ResponseEntity.noContent().build();
        }
        if (decision instanceof SuggestionDecision.Failed failed) {
            SuggestionErrorCode errorCode = SuggestionErrorCode.of(failed.reason());
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ErrorResponse.of(errorCode.getCode(), errorCode.getMessage(), traceId()));
        }
        // 판정 결과는 봉인된 타입이라 위 셋이 전부다.
        throw new IllegalStateException("처리하지 않은 판정 결과다: " + decision);
    }

    // 로그와 응답을 같은 식별자로 묶는다. 다른 API 의 예외 처리기와 같은 방식이다.
    private String traceId() {
        return MDC.get("traceId");
    }
}
