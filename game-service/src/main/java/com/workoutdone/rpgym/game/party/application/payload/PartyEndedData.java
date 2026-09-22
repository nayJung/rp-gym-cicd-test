package com.workoutdone.rpgym.game.party.application.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 수명 만료. 퀘스트 담당의 정리 트리거. weeklyXp 는 종료 시점에 최종 재계산한 값. */
public record PartyEndedData(
        UUID partyId,
        String partyName,
        List<UUID> memberUserIds,
        long weeklyXp,
        Instant endedAt
) {
}
