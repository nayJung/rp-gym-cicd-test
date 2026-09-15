package com.workoutdone.rpgym.game.party.application.payload;

import java.time.Instant;
import java.util.UUID;

/** 알림 서비스가 invitee 에게 Push 를 보낼 때 쓴다. envelope.userId = invitee. */
public record PartyInvitedData(
    UUID invitationId,
    UUID partyId,
    String partyName,
    UUID inviterId,
    UUID inviteeId,
    Instant expiresAt
) {
}
