package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PartyInvitationJpaRepository extends JpaRepository<PartyInvitation, UUID> {

    @Query("""
            SELECT i FROM PartyInvitation i
             WHERE i.inviteeId = :inviteeId
               AND i.status = :status
               AND i.expiresAt > :now
             ORDER BY i.createdAt DESC
            """)
    List<PartyInvitation> findPending(@Param("inviteeId") UUID inviteeId,
                                      @Param("status") InvitationStatus status,
                                      @Param("now") Instant now);

    long countByPartyIdAndStatus(UUID partyId, InvitationStatus status);

    boolean existsByPartyIdAndInviteeIdAndStatus(UUID partyId, UUID inviteeId, InvitationStatus status);

    /*
     * 수락 / 거절 / 취소 전이. WHERE status = PENDING 이 핵심이다.
     * 수락과 거절이 동시에 오면 먼저 커밋되는 쪽만 affected = 1 이고 다른 쪽은 0 이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PartyInvitation i
               SET i.status = :to, i.respondedAt = :now
             WHERE i.id = :id
               AND i.status = :pending
            """)
    int transition(@Param("id") UUID id,
                   @Param("pending") InvitationStatus pending,
                   @Param("to") InvitationStatus to,
                   @Param("now") Instant now);

    /*
     * 파티 마감 · 해산 때 거둘 초대. 예전에는 UPDATE 한 문장으로 한꺼번에 CANCELED 로 바꿨지만,
     * 그러면 어떤 행이 실제로 바뀌었는지 몰라 PARTY_INVITATION_CLOSED 를 실을 수 없다.
     * 한 파티의 PENDING 은 정원(4) 미만이라 행을 읽어 와도 건수가 작다.
     */
    List<PartyInvitation> findByPartyIdAndStatus(UUID partyId, InvitationStatus status);

    /** 만료 배치. idx_party_invitations_expires 를 탄다. 오래된 것부터 limit 만큼. */
    @Query("""
            SELECT i FROM PartyInvitation i
             WHERE i.status = :status
               AND i.expiresAt <= :now
             ORDER BY i.expiresAt ASC
            """)
    List<PartyInvitation> findExpired(@Param("status") InvitationStatus status,
                                      @Param("now") Instant now,
                                      Pageable pageable);
}
