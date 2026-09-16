package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

////중복 검사는 서비스가 함.
public record InviteRequest(
        @NotBlank(message = "inviteeIds 는 비어있을수 없습니다.")
        @Size(max = 3, message = "한 번에 최대 3명까지 초대할수있습니다.")
        List<UUID> inviteeIds
) {
}
