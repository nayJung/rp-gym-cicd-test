package com.workoutdone.rpgym.game.party.application.view;

import com.workoutdone.rpgym.game.party.domain.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

public record RejectResultView(
        UUID invitationId,
        InvitationStatus invitationStatus,
        Instant responedAt
) {
}
