package com.workoutdone.rpgym.game.party.application.payload;

import java.time.Instant;
import java.util.UUID;

public record PartyMemberJoinedData(
        UUID partyId,
        UUID memberId,
        UUID userId,
        String role,
        int memberCount,
        int maxMember,
        Instant joinedAt
) {

}
