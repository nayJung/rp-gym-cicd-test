package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.application.view.AcceptOutcome;

import java.time.Instant;
import java.util.UUID;

public record AcceptInvitationResponse(
        UUID invitationId,
        String invitationStatus,
        UUID partyId,
        String partyStatus,
        int memberCount,
        int maxMember,
        Instant joinedAt
) {
    public static AcceptInvitationResponse from(AcceptOutcome.Joined j) {
        return new AcceptInvitationResponse(
                j.invitationId(),
                j.invitationStatus().name(),
                j.partyId(),
                j.partyStatus().name(),
                j.memberCount(),
                j.maxMember(),
                j.joinedAt());
    }
}
