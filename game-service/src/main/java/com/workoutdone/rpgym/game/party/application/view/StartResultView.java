package com.workoutdone.rpgym.game.party.application.view;

import java.time.Instant;
import java.util.UUID;

public record StartResultView(
        UUID partyId,
        int memberCount,
        int maxMember,
        int canceledInvitationCount,
        Instant startedAt,
        Instant endsAt
) {
}
