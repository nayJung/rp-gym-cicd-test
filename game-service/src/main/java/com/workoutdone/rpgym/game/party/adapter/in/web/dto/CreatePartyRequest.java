package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * metric 은 필수. 값이 삼종 밖이면 Jackson 역직렬화 단계에서 400 이 난다 (enum 매핑 실패).
 * null 이면 @NotNull 이 400 을 낸다. 둘 다 컨트롤러에 안 들어온다.
 */
public record CreatePartyRequest(
        @NotBlank(message = "파티 이름을 필수입니다.")
        @Size(max = 50, message = "파티 이름은 최대 50자 입니다.")
        String partyName,
        PartyVisibility visibility, //visibility 는 선택. null 이면 서비스가 PRIVATE 로
        @NotNull(message = "메트릭은 필수입니다. STEPS / ACTIVE_MINUTES / ACTIVE_CALORIES 중 하나.")
        PartyMetric metric
) {
}
