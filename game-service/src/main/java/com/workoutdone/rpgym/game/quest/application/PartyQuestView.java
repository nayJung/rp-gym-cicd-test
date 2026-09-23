package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.quest.domain.Metric;
import com.workoutdone.rpgym.game.quest.domain.QuestStatus;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 조회 경계 타입이다. 도메인 엔티티를 웹으로 그대로 내보내지 않는다.
// 내보내면 컬럼 하나 바꿀 때마다 응답 규격이 따라 바뀐다.
public record PartyQuestView(
        UUID partyQuestId,
        UUID partyId,
        String title,
        Metric metric,
        int targetValue,
        int currentValue,
        QuestStatus status,
        int rewardXp,
        Instant startedAt,
        Instant expiredAt,
        List<MemberView> members
) {

    // baselineValue 가 비어 있으면 그 멤버는 아직 한 번도 동기화한 적이 없다는 뜻이다.
    // 첫 이벤트가 도착할 때 채워진다.
    public record MemberView(UUID userId, Integer baselineValue, int contributedValue) {}

    public static PartyQuestView from(PartyQuest partyQuest, List<PartyQuestMember> members) {
        return new PartyQuestView(
                partyQuest.getPartyQuestId(),
                partyQuest.getPartyId(),
                partyQuest.getTitle(),
                partyQuest.getMetric(),
                partyQuest.getTargetVal(),
                partyQuest.getCurrentVal(),
                partyQuest.getStatus(),
                partyQuest.getRewardXp(),
                partyQuest.getStartedAt(),
                partyQuest.getExpiredAt(),
                members.stream()
                        .map(member -> new MemberView(
                                member.getUserId(), member.getBaselineVal(), member.getContributedVal()))
                        .toList()
        );
    }
}
