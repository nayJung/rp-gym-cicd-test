package com.workoutdone.rpgym.game.party.application.view;

import java.time.Instant;
import java.util.UUID;

////초대 한 건의 결과. 부분 성공을 표현하려고 예외 대신 값으로 돌려준다.
public record InvitationResultView(
        UUID inviteeId,
        Result result,
        UUID invitationId,
        Instant expiresAt
) {
    public enum Result {
        CREATED,
        ALREADY_MEMBER,
        ALREADY_IN_PARTY,
        ALREADY_INVITED,
        SELF_INVITE
    }

    public static InvitationResultView created(UUID inviteeId, UUID invitationId, Instant expiresAt){
        return new InvitationResultView(inviteeId, Result.CREATED, invitationId, expiresAt);
    }

    public static InvitationResultView skipped(UUID inviteeId, Result reason){
        return new InvitationResultView(inviteeId,reason,null,null);
    }
}
