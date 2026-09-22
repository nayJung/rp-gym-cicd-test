package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

////중복 검사는 서비스가 함.
public record InviteRequest(
        // List 에는 @NotBlank 를 못 쓴다 (CharSequence 전용 — 붙이면 검증 시점에 UnexpectedTypeException → 500).
        @NotEmpty(message = "inviteeIds 는 비어있을수 없습니다.")
        @Size(max = 3, message = "한 번에 최대 3명까지 초대할수있습니다.")
        List<UUID> inviteeIds
) {
}
