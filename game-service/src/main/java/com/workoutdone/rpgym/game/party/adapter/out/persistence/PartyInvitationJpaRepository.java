package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PartyInvitation i
               SET i.status = :to
             WHERE i.partyId = :partyId
               AND i.status = :pending
            """)
    int transitionAllByParty(@Param("partyId") UUID partyId,
                             @Param("pending") InvitationStatus pending,
                             @Param("to") InvitationStatus to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PartyInvitation i
               SET i.status = :to
             WHERE i.status = :pending
               AND i.expiresAt <= :now
            """)
    int expireAll(@Param("pending") InvitationStatus pending,
                  @Param("to") InvitationStatus to,
                  @Param("now") Instant now);
}
