package com.workoutdone.rpgym.game.party.application.view;

import java.time.Instant;
import java.util.UUID;

////받은 초대 목록 한 줄. memberCount 는 조회 시점 값.
public record InvitationView(
        UUID invitationId,
        UUID partyId,
        String partyName,
        UUID inviterId,
        int memberCount,
        int maxMember,
        Instant createdAt,
        Instant expiresAt
) {
}
