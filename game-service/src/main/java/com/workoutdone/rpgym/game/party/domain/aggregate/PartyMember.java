package com.workoutdone.rpgym.game.party.domain.aggregate;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedEntity;
import com.workoutdone.rpgym.game.party.domain.MemberRole;
import com.workoutdone.rpgym.game.party.domain.MemberStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


/**
 * 파티 소속 이력 한 건. "누가 어느 파티에 언제부터 언제까지"
 *
 * 행을 지우지 않는다. left_at이 NULL이면 소속중이다.
 * 주간 랭킹은 joined_at, left_at 구간과 xp_ledgers.occurred_at의 교집합으로 집계한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "party_members", schema = "game_service")
public class PartyMember extends BaseCreatedUpdatedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "id", nullable = false, updatable = false)
    private UUID partyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    public static PartyMember owner(UUID id, UUID partyId, UUID userId, Instant joinedAt) {
        return create(id, partyId, userId, MemberRole.OWNER, joinedAt);
    }

    public static PartyMember member(UUID id, UUID partyId, UUID userId, Instant joinedAt) {
        return create(id, partyId, userId, MemberRole.MEMBER, joinedAt);
    }

    private static PartyMember create(UUID id, UUID partyId, UUID userId, MemberRole role, Instant joinedAt) {
        PartyMember m = new PartyMember();
        m.id = id;
        m.partyId = partyId;
        m.userId = userId;
        m.role = role;
        m.status = MemberStatus.ACTIVE;
        m.joinedAt = joinedAt;
        return m;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isOwner() {
        return role == MemberRole.OWNER;
    }

    /** 소속 종료. 두 번 호출되면 예외 — 탈퇴 API 는 ACTIVE 행만 찾으므로 정상 경로에선 안 일어난다. */
    public void leave(Instant at) {
        if (status != MemberStatus.ACTIVE) {
            throw new IllegalStateException("이미 탈퇴한 멤버입니다: " + id);
        }
        this.status = MemberStatus.LEFT;
        this.leftAt = at;
    }

    public void promoteToOwner() {
        this.role = MemberRole.OWNER;
    }
}
