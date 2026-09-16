package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePartyRequest(
        @NotBlank(message = "파티 이름을 필수입니다.")
        @Size(max = 50, message = "파티 이름은 최대 50자 입니다.")
        String partyName,
        PartyVisibility visibility //visibility 는 선택. null 이면 서비스가 PRIVATE 로
) {
}
