package com.workoutdone.rpgym.game.party.domain.repo;

import com.workoutdone.rpgym.game.party.domain.aggregate.Party;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyRepository {

    Party save(Party party);

    Optional<Party> findById(UUID partyId);

    /** 탈퇴 · 종료처럼 여러 행을 함께 바꾸는 경로에서 파티 행을 잠근다 (SELECT ... FOR UPDATE). */
    Optional<Party> findByIdForUpdate(UUID partyId);

    List<Party> findAllByIds(Collection<UUID> partyIds);

    /**
     * 정원 확보. 검증과 증가를 한 문장으로 한다.
     * <pre>
     * UPDATE parties SET current_member = current_member + 1
     *  WHERE id = ? AND status = 'RECRUITING' AND matching_deadline_at > now AND current_member < max_member
     * </pre>
     * @return true = 자리 확보. false = 찼거나 마감됐거나 모집 중이 아님
     */
    boolean reserveSeat(UUID partyId, Instant now);

    /**
     * RECRUITING → ACTIVE. 조건부라 동시에 두 경로(4/4 도달 · 파티장 start · 배치)가 와도 한 번만 성공한다.
     * @return true = 이 호출이 전이시켰다. false = 이미 RECRUITING 이 아니었다
     */
    boolean closeRecruiting(UUID partyId, Instant now);

    /** 파티장 본인이 RECRUITING 파티를 닫는다. owner 검증까지 한 문장에 넣는다. */
    boolean closeRecruitingByOwner(UUID partyId, UUID ownerId, Instant now);

    /** 자동 매칭 후보. PUBLIC · RECRUITING · 빈자리 · 마감 전. 거의 찬 파티부터. */
    List<Party> findMatchingCandidates(Instant now, int limit);

    /** 배치용: 마감 시각이 지났는데 아직 RECRUITING 인 파티 */
    List<Party> findRecruitingPastDeadline(Instant now, int limit);

    /** 배치용: 수명이 다했는데 아직 ACTIVE 인 파티 */
    List<Party> findActivePastEnd(Instant now, int limit);
}
