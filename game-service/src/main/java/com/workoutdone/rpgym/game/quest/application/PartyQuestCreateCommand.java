package com.workoutdone.rpgym.game.quest.application;

import java.util.UUID;

// 파티장이 파티 퀘스트를 만들 때 도메인으로 넘기는 값이다.
// 명단과 지표는 여기 없다. 파티에서 읽는다.
// rewardXp 도 받지 않는다. 파티장이 정하게 하면 원하는 만큼 XP 를 만들어낼 수 있다.
public record PartyQuestCreateCommand(
        UUID partyId,
        UUID requesterId,
        String title,
        int targetValue
) {
}
