package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.application.view.MatchingResultView;

import java.time.Instant;
import java.util.UUID;

public record MatchingResponse(
        String result,
        UUID partyId,
        String partyName,
        String partyStatus,
        int memberCount,
        int maxMember,
        Instant matchingDeadlineAt
) {

    public static MatchingResponse from(MatchingResultView v){
        return new MatchingResponse(v.result().name(),
                v.partyId(), v.partyName(), v.partyStatus().name(),
        v.memberCount(), v.maxMember(), v.matchingDeadlineAt());
    }
}
