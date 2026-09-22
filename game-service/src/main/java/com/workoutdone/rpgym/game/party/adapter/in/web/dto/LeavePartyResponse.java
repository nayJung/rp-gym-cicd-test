package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.application.view.LeaveResultView;

import java.time.Instant;
import java.util.UUID;

public record LeavePartyResponse(
        UUID partyId,
        String partyStatus,
        int memberCount,
        UUID newOwnerId,
        Instant leftAt
) {

    public static LeavePartyResponse from(LeaveResultView v){
        return new LeavePartyResponse(
                v.partyId(),
                v.partyStatus().name(),
                v.memberCount(),
                v.newOwnerId(),
                v.leftAt());
    }
}
