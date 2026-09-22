package com.workoutdone.rpgym.game.party.application.payload;

import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;

import java.time.Instant;
import java.util.UUID;

/**
 * 응답 없이 닫힌 초대. 알림 서비스가 이 invitationId 로 보낸 슬랙 메시지를 찾아 버튼을 거둔다.
 * envelope.userId = invitee (PARTY_INVITED 와 같은 사람) 이라 메시지 대상이 바뀌지 않는다.
 */
public record PartyInvitationClosedData(
        UUID invitationId,
        UUID partyId,
        String partyName,
        UUID inviteeId,
        String reason,
        String status,
        Instant closedAt
) {
    public static PartyInvitationClosedData of(UUID invitationId, UUID partyId, String partyName,
                                               UUID inviteeId, InvitationCloseReason reason, Instant closedAt) {
        return new PartyInvitationClosedData(invitationId, partyId, partyName, inviteeId,
                reason.name(), reason.status().name(), closedAt);
    }
}
