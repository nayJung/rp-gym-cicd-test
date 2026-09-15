package com.workoutdone.rpgym.game.party.domain.repo;

import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyInvitationRepository {

    PartyInvitation save(PartyInvitation invitation);

    Optional<PartyInvitation> findById(UUID invitationId);

    /** 받은 초대. PENDING 이고 아직 안 만료된 것만, 최신순. */
    List<PartyInvitation> findPendingByInvitee(UUID inviteeId, Instant now);

    long countPendingByPartyId(UUID partyId);

    boolean existsPendingByPartyIdAndInviteeId(UUID partyId, UUID inviteeId);

    /** PENDING → ACCEPTED. 이미 PENDING 이 아니면 0. */
    boolean markAccepted(UUID invitationId, Instant now);

    /** PENDING → REJECTED. 이미 PENDING 이 아니면 0. */
    boolean markRejected(UUID invitationId, Instant now);

    /** PENDING → CANCELED (한 건). 수락하려는데 자리가 없을 때. */
    boolean markCanceled(UUID invitationId);

    /** 파티 마감 시 남은 PENDING 전부 CANCELED. */
    int cancelAllPending(UUID partyId);

    /** 배치용: 만료 시각이 지난 PENDING → EXPIRED. */
    int expirePending(Instant now);
}
