package com.workoutdone.rpgym.game.quest.application.payload;

import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 파티 퀘스트 생성 이벤트의 본문이다.
// PartyQuestCompletedData 와 같은 모양이다. 받는 쪽이 명단을 돌며 알림을 보내는 코드를 두 이벤트에 같이 쓴다.
// ownerId 를 따로 싣는 이유는 파티장도 members 에 들어 있어서다.
// 파티장은 생성 요청의 응답을 이미 받았으므로 카드가 중복일 수 있는데, 보낼지 말지는 알림 쪽이 정한다.
// startedAt 은 봉투의 occurredAt 과 같은 값이라 넣지 않는다. expiredAt 은 봉투에 없어서 싣는다.
// 기여도는 생성 시점에 전원 0 이라 담지 않는다.
public record PartyQuestCreatedData(
        UUID partyQuestId,
        UUID partyId,
        UUID ownerId,
        String title,
        String metric,
        int targetValue,
        int rewardXp,
        Instant expiredAt,
        List<Member> members
) {

    public record Member(UUID userId) {}

    public static PartyQuestCreatedData from(
            PartyQuest partyQuest, UUID ownerId, List<PartyQuestMember> members) {
        return new PartyQuestCreatedData(
                partyQuest.getPartyQuestId(),
                partyQuest.getPartyId(),
                ownerId,
                partyQuest.getTitle(),
                partyQuest.getMetric().name(),
                partyQuest.getTargetVal(),
                partyQuest.getRewardXp(),
                partyQuest.getExpiredAt(),
                members.stream()
                        .map(member -> new Member(member.getUserId()))
                        .toList()
        );
    }
}
