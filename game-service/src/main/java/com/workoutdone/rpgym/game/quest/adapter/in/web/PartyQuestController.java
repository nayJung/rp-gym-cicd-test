package com.workoutdone.rpgym.game.quest.adapter.in.web;

import com.workoutdone.rpgym.common.response.ErrorResponse;
import com.workoutdone.rpgym.game.quest.adapter.in.web.dto.CreatePartyQuestRequest;
import com.workoutdone.rpgym.game.quest.adapter.in.web.dto.PartyQuestResponse;
import com.workoutdone.rpgym.game.quest.application.PartyQuestCreateCommand;
import com.workoutdone.rpgym.game.quest.application.PartyQuestCreateService;
import com.workoutdone.rpgym.game.quest.application.PartyQuestCreation;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 파티장이 파티 퀘스트를 만든다.
// 파티가 만들어질 때 자동으로 생기지 않는다.
// 무엇을 얼마나 할지는 사람이 정하는 일이고, 자동으로 만들면 목표값과 지표를 누가 정하느냐는
// 문제가 생긴다.
@RestController
@RequestMapping("/api/v1/games/party-quests")
@RequiredArgsConstructor
public class PartyQuestController {

    private final PartyQuestCreateService partyQuestCreateService;

    @PostMapping
    public ResponseEntity<Object> create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody CreatePartyQuestRequest request
    ) {
        PartyQuestCreation creation = partyQuestCreateService.create(new PartyQuestCreateCommand(
                request.partyId(),
                userId,
                request.title(),
                request.metric(),
                request.targetValue(),
                request.memberUserIds()
        ));

        if (creation instanceof PartyQuestCreation.Created created) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(PartyQuestResponse.from(created.view()));
        }
        if (creation instanceof PartyQuestCreation.Failed failed) {
            PartyQuestErrorCode errorCode = PartyQuestErrorCode.of(failed.reason());
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ErrorResponse.of(errorCode.getCode(), errorCode.getMessage(), MDC.get("traceId")));
        }
        // 생성 결과는 봉인된 타입이라 위 둘이 전부다.
        throw new IllegalStateException("처리하지 않은 생성 결과다: " + creation);
    }
}
