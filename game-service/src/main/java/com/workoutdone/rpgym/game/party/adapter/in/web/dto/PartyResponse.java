package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.application.view.PartyView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartyResponse(
        UUID partyId,
        String partyName,
        UUID ownerId,
        String status,
        String visibility,
        String metric,
        int memberCount,
        int maxMember,
        Instant matchingDeadlineAt,
        Instant endsAt,
        Instant createdAt,
        List<Member> members
) {
    public record Member(UUID userId, String role, Instant joinedAt){}

    public static PartyResponse from(PartyView v){
        return new PartyResponse(
                v.partyId(), v.partyName(), v.ownerId(), v.status().name(),
                v.visibility().name(), v.metric().name(), v.memberCount(), v.maxMember(),
                v.matchingDeadlineAt(), v.endsAt(), v.createdAt(),
                v.members().stream().map(m -> new Member(m.userId(),
                        m.role().name(), m.joinedAt())).toList());
    }
}
