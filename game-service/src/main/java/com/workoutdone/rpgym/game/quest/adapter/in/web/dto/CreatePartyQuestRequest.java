package com.workoutdone.rpgym.game.quest.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

// 파티 퀘스트 생성 요청이다.
// memberUserIds 와 metric 은 지금만 요청으로 받는다.
// 파티 담당자의 테이블을 읽게 되면 partyId 하나로 명단과 지표를 모두 가져오고 두 필드는 사라진다.
// 그 전에도 판정과 동시성 실험을 돌릴 수 있어야 해서 이렇게 열어뒀다.
public record CreatePartyQuestRequest(
        UUID partyId,
        String title,
        String metric,
        int targetValue,
        List<UUID> memberUserIds
) {
}
