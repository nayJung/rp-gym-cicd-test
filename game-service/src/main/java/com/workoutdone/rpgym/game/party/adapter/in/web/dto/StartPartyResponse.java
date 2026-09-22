package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.application.view.StartResultView;

import java.time.Instant;
import java.util.UUID;

public record StartPartyResponse(
        UUID partyId,
        String status,
        int memberCount,
        int maxMember,
        int canceledInvitationCount,
        Instant startedAt,
        Instant endsAt
) {

    public static StartPartyResponse from(StartResultView v){
        return new StartPartyResponse(
                v.partyId(), "ACTIVE", v.memberCount(),
                v.maxMember(), v.canceledInvitationCount(),
                v.startedAt(), v.endsAt());
    }
}
