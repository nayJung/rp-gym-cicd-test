package com.workoutdone.rpgym.game.party.domain.aggregate;


import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedEntity;
import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 초대장. 상태 전이만 있고 삭제는 없다.
 *
 * PENDING → ACCEPTED / REJECTED 전이는 이 엔티티의 메서드가 아니라
 * {@code PartyInvitationRepository.markAccepted / markRejected} 의 조건부 UPDATE 로 한다.
 * 수락과 거절이 동시에 오면 먼저 온 쪽만 성공해야 하는데, 엔티티를 읽고-바꾸고-저장하면
 * 그 사이에 다른 쪽이 끼어들 수 있기 때문이다.
 */
@Entity
@Getter
@Table(name = "party_invitations", schema = "game_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyInvitation extends BaseCreatedUpdatedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "party_id", nullable = false, updatable = false)
    private UUID partyId;

    @Column(name = "inviter_id", nullable = false, updatable = false)
    private UUID inviterId;

    @Column(name = "invitee_id", nullable = false, updatable = false)
    private UUID inviteeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public static PartyInvitation create(UUID id, UUID partyId, UUID inviterId,
                                         UUID inviteeId, Instant now, Duration ttl){
        if (inviterId.equals(inviteeId)){
            throw new IllegalArgumentException("자기 자신을 초대할수 없습니다.");
        }
        PartyInvitation inv = new PartyInvitation();
        inv.id = id;
        inv.partyId = partyId;
        inv.inviterId = inviterId;
        inv.inviteeId = inviteeId;
        inv.status = InvitationStatus.PENDING;
        inv.expiresAt = now.plus(ttl);
        return inv;
    }

    public boolean isInvitee(UUID userId) {
        return inviteeId.equals(userId);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    /** DB 상태가 PENDING 이어도 만료 시각이 지났으면 만료다 (lazy 판정). 정리 배치는 나중에 EXPIRED 로 바꾼다. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isAcceptable(Instant now) {
        return isPending() && !isExpired(now);
    }
}
