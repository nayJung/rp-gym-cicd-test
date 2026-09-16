package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.workoutdone.rpgym.game.party.domain.InvitationStatus.ACCEPTED;
import static com.workoutdone.rpgym.game.party.domain.InvitationStatus.CANCELED;
import static com.workoutdone.rpgym.game.party.domain.InvitationStatus.EXPIRED;
import static com.workoutdone.rpgym.game.party.domain.InvitationStatus.PENDING;
import static com.workoutdone.rpgym.game.party.domain.InvitationStatus.REJECTED;

@Repository
@RequiredArgsConstructor
public class PartyInvitationRepositoryImpl implements PartyInvitationRepository {

    private final PartyInvitationJpaRepository jpa;

    @Override
    public PartyInvitation save(PartyInvitation invitation) {
        return jpa.save(invitation);
    }

    @Override
    public Optional<PartyInvitation> findById(UUID invitationId) {
        return jpa.findById(invitationId);
    }

    @Override
    public List<PartyInvitation> findPendingByInvitee(UUID inviteeId, Instant now) {
        return jpa.findPending(inviteeId, PENDING, now);
    }

    @Override
    public long countPendingByPartyId(UUID partyId) {
        return jpa.countByPartyIdAndStatus(partyId, PENDING);
    }

    @Override
    public boolean existsPendingByPartyIdAndInviteeId(UUID partyId, UUID inviteeId) {
        return jpa.existsByPartyIdAndInviteeIdAndStatus(partyId, inviteeId, PENDING);
    }

    @Override
    public boolean markAccepted(UUID invitationId, Instant now) {
        return jpa.transition(invitationId, PENDING, ACCEPTED, now) == 1;
    }

    @Override
    public boolean markRejected(UUID invitationId, Instant now) {
        return jpa.transition(invitationId, PENDING, REJECTED, now) == 1;
    }

    @Override
    public boolean markCanceled(UUID invitationId) {
        // CANCELED 는 사용자 응답이 아니라 responded_at 을 남기지 않는다.
        return jpa.transition(invitationId, PENDING, CANCELED, null) == 1;
    }

    @Override
    public int cancelAllPending(UUID partyId) {
        return jpa.transitionAllByParty(partyId, PENDING, CANCELED);
    }

    @Override
    public int expirePending(Instant now) {
        return jpa.expireAll(PENDING, EXPIRED, now);
    }
}
