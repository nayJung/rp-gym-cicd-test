package com.workoutdone.rpgym.game.quest.application;

import java.util.List;
import java.util.UUID;

// 파티장이 파티 퀘스트를 만들 때 도메인으로 넘기는 값이다.
// memberUserIds 를 요청으로 받는 것은 지금만이다.
// 파티 담당자의 party_members 를 읽을 수 있게 되면 partyId 만으로 명단을 가져오고 이 필드는 사라진다.
// 지금 이렇게 둔 이유는, 그쪽 코드가 들어오기 전에도 판정과 동시성 실험을 돌릴 수 있어야 하기 때문이다.
// metric 도 같다. 파티가 이미 자기 지표를 확정해서 들고 있으므로
// 나중에는 파티에서 읽고 이 필드는 없어진다.
// 지금 문자열로 받는 것은 세 종류 밖의 값이 올 수 있어서다. 파싱 실패가 곧 판정이다.
// rewardXp 는 받지 않는다. 파티장이 정하게 하면 원하는 만큼 XP 를 만들어낼 수 있다.
public record PartyQuestCreateCommand(
        UUID partyId,
        UUID requesterId,
        String title,
        String metric,
        int targetValue,
        List<UUID> memberUserIds
) {
}
