package com.workoutdone.rpgym.game.party.application.view;

import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchingResultView(
        Result result,
        UUID partyId,
        String partyName,
        PartyMetric metric,
        PartyStatus partyStatus,
        int memberCount,
        int maxMember,
        Instant matchingDeadlineAt
) {
    public enum Result {MATCHED, WAITING}
}
