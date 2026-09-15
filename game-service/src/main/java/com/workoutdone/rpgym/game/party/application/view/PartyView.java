package com.workoutdone.rpgym.game.party.application.view;

import com.workoutdone.rpgym.game.party.domain.MemberRole;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record PartyView(

        UUID partyId,
        String partyName,
        UUID ownerId,
        PartyStatus status,
        PartyVisibility visibility,
        int memberCount,
        int maxMember,
        Instant matchingDeadlineAt, //// ACTIVE 이후엔 null
        Instant endsAt,
        Instant createdAt,
        List<MemberView> members
) {
    public record MemberView(UUID userId, MemberRole role, Instant joinedAt){
        public static MemberView from(PartyMember m){
            return new MemberView(m.getUserId(), m.getRole(), m.getJoinedAt());
        }
    }

    public static PartyView of(Party party, List<PartyMember> members, Instant now){
        PartyStatus status = party.displayStatus(now);
        return new PartyView(
                party.getId(),
                party.getPartyName(),
                party.getOwnerId(),
                status,
                party.getVisibility(),
                party.getCurrentMember(),
                party.getMaxMember(),
                status == PartyStatus.RECRUITING ? party.getMatchingDeadlienAt() : null,
                party.getEndsAt(),
                party.getCreatedAt() == null ? null : party.getCreatedAt().toInstant(ZoneOffset.UTC),
                members.stream().map(MemberView::from).toList()
        );
    }
}
