package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 같은 metric 파티끼리만 매칭된다. 빈 파티가 없어 새로 만들 때도 이 metric 으로 만든다. */
public record MatchingRequest(
        @Size(max = 50, message = "파티 이름은 최대 50자입니다.")
        String partyName,
        @NotNull(message = "메트릭은 필수입니다. STEPS / ACTIVE_MINUTES / ACTIVE_CALORIES 중 하나.")
        PartyMetric metric
) {
}
