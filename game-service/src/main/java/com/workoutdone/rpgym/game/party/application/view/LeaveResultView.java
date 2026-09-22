package com.workoutdone.rpgym.game.party.application.view;

import com.workoutdone.rpgym.game.party.domain.PartyStatus;

import java.time.Instant;
import java.util.UUID;

public record LeaveResultView(
        UUID partyId,
        PartyStatus partyStatus,
        int memberCount,
        UUID newOwnerId,
        Instant leftAt
) {
}
