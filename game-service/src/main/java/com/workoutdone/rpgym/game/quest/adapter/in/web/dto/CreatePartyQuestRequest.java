package com.workoutdone.rpgym.game.quest.adapter.in.web.dto;

import java.util.UUID;

// 파티 퀘스트 생성 요청이다.
// 명단과 지표를 받지 않는다. 둘 다 파티가 이미 확정해서 들고 있는 값이라 partyId 하나로 읽는다.
// 요청자가 보낸 명단으로 "요청자가 그 명단에 있는가" 를 검사하는 것은
// 요청자가 준 데이터를 요청자가 준 데이터로 검사하는 것이라 인가가 성립하지 않는다.
public record CreatePartyQuestRequest(
        UUID partyId,
        String title,
        int targetValue
) {
}
