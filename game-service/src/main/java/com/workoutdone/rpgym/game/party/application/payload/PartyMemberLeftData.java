package com.workoutdone.rpgym.game.party.application.payload;

import java.time.Instant;
import java.util.UUID;

/** newOwnerId 는 승계가 일어났을 때만, partyStatus 는 탈퇴 후 상태 (DISBANDED 면 해산). */
public record PartyMemberLeftData(
        UUID partyId,
        UUID memberId,
        UUID userId,
        int memberCount,
        UUID newOwnerId,
        String partyStatus,
        Instant leftAt
) {

}