package com.workoutdone.rpgym.game.quest.adapter.in.web.dto;

import com.workoutdone.rpgym.game.quest.application.PartyQuestView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 지표와 상태를 문자열로 내보낸다.
// enum 을 그대로 노출하면 상수 이름을 바꾸는 순간 API 규격이 함께 바뀐다.
public record PartyQuestResponse(
        UUID partyQuestId,
        UUID partyId,
        String title,
        String metric,
        int targetValue,
        int currentValue,
        String status,
        int rewardXp,
        Instant startedAt,
        Instant expiredAt,
        List<MemberResponse> members
) {

    public record MemberResponse(UUID userId, Integer baselineValue, int contributedValue) {}

    public static PartyQuestResponse from(PartyQuestView view) {
        return new PartyQuestResponse(
                view.partyQuestId(),
                view.partyId(),
                view.title(),
                view.metric().name(),
                view.targetValue(),
                view.currentValue(),
                view.status().name(),
                view.rewardXp(),
                view.startedAt(),
                view.expiredAt(),
                view.members().stream()
                        .map(member -> new MemberResponse(
                                member.userId(), member.baselineValue(), member.contributedValue()))
                        .toList()
        );
    }
}
