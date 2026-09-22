package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.workoutdone.rpgym.game.party.application.view.InvitationResultView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InviteResponse(
        List<Result> results
) {
    //CREATED가 아니면 invitationId / expiresAt을 아예 안실음.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(UUID inviteeId, String result, UUID invitationId, Instant expiresAt){}

    public static InviteResponse from(List<InvitationResultView> views){
        return new InviteResponse(views.stream()
                .map(v -> new Result(v.inviteeId(), v.result().name(),
                        v.invitationId(), v.expiresAt()))
                .toList());
    }
}
