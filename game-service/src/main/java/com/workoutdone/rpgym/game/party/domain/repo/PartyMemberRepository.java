package com.workoutdone.rpgym.game.party.domain.repo;

import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyMemberRepository {

    PartyMember save(PartyMember member);

    /** 사용자의 현재 소속. 1인 1파티라 최대 한 건이다. */
    Optional<PartyMember> findActiveByUserId(UUID userId);

    Optional<PartyMember> findActiveByPartyIdAndUserId(UUID partyId, UUID userId);

    /** joined_at 오름차순. 첫 원소가 승계 1순위다. */
    List<PartyMember> findActiveByPartyId(UUID partyId);

    List<UUID> findActiveUserIdsByPartyId(UUID partyId);

    /** 파티 종료 시 전원 LEFT. left_at = 파티의 ends_at. */
    int leaveAllActive(UUID partyId, Instant leftAt);

    /** 주간 파티 XP = Σ (소속 기간 ∩ [weekStart, weekEnd) 안의 xp_ledgers.amount) */
    long sumWeeklyXp(UUID partyId, Instant weekStart, Instant weekEnd);
}
