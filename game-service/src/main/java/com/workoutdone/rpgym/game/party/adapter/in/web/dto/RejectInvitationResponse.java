package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.application.view.RejectResultView;

import java.time.Instant;
import java.util.UUID;

public record RejectInvitationResponse(
        UUID invitationId,
        String invitationStatus,
        Instant respondedAt
) {
    public static RejectInvitationResponse from(RejectResultView v){
        return new RejectInvitationResponse(v.invitationId(),
                v.invitationStatus().name(), v.responedAt());
    }
}
