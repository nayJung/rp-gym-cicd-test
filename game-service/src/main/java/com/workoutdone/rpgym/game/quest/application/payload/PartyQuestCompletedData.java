package com.workoutdone.rpgym.game.quest.application.payload;

import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 파티 퀘스트 완료 이벤트의 본문이다.
// 멤버별로 이벤트를 나눠 보내지 않고 한 건에 명단을 담는다.
// 아웃박스에 걸린 제약이 "하나의 대상은 같은 종류의 이벤트를 한 번만 발행한다" 라서,
// 같은 파티 퀘스트로 네 건을 넣으려면 그 제약을 풀어야 한다.
// 그 제약이 중복 발행을 막는 마지막 선이라 풀지 않는 쪽을 택했다.
// 받는 쪽은 members 를 돌면서 각자에게 알림을 보내면 된다.
public record PartyQuestCompletedData(
        UUID partyQuestId,
        UUID partyId,
        String title,
        String metric,
        int targetValue,
        int currentValue,
        int rewardXp,
        Instant completedByMeasuredAt,
        List<Member> members
) {

    public record Member(UUID userId, int contributedValue) {}

    public static PartyQuestCompletedData from(
            PartyQuest partyQuest, List<PartyQuestMember> members, Instant completedByMeasuredAt) {
        return new PartyQuestCompletedData(
                partyQuest.getPartyQuestId(),
                partyQuest.getPartyId(),
                partyQuest.getTitle(),
                partyQuest.getMetric().name(),
                partyQuest.getTargetVal(),
                // 목표를 넘길 수 있다. 완료 직후 도착한 기여도 합계에는 그대로 쌓이기 때문이다.
                // 화면에서 100 퍼센트로 자를지는 보여주는 쪽이 정한다.
                partyQuest.getCurrentVal(),
                partyQuest.getRewardXp(),
                completedByMeasuredAt,
                members.stream()
                        .map(member -> new Member(member.getUserId(), member.getContributedVal()))
                        .toList()
        );
    }
}
