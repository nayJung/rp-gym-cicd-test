package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

public record MatchingRequest(
        @Size(max = 50, message = "파티 이름은 최대 50자입니다.")
        String partyName
) {
}
