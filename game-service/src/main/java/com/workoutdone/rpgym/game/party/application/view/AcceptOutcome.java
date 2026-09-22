package com.workoutdone.rpgym.game.party.application.view;

import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;

import java.time.Instant;
import java.util.UUID;

//// 수락 결과.
/// 정원이 찬 경우를 예외가 아니라 값으로 돌려줌: 그 때 초대를 CANCELED로 바꿔 저장해야 하는데, 예외를 던지면 트랜잭션이 롤백돼 그 저장까지 사라진다.
/// 컨트롤러가 FULL 을 받으면 커밋이 끝난뒤에 409로 바꾼다.
public sealed interface AcceptOutcome {

    record Joined(
            UUID invitationId,
            InvitationStatus invitationStatus,
            UUID partyId,
            PartyStatus partyStatus,
            int memberCount,
            int maxMember,
            Instant joinedAt
    ) implements AcceptOutcome{}

    record Full(UUID invitationId) implements AcceptOutcome{}
}
