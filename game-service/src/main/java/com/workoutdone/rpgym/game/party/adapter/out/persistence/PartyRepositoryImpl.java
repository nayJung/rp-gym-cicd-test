package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PartyRepositoryImpl implements PartyRepository {

    private final PartyJpaRepository jpa;

    @Override
    public Party save(Party party) {
        return jpa.save(party);
    }


    @Override
    public Optional<Party> findById(UUID partyId) {
        return jpa.findById(partyId);
    }


    @Override
    public Optional<Party> findByIdForUpdate(UUID partyId) {
        return jpa.findByIdForUpdate(partyId);
    }

    @Override
    public List<Party> findAllByIds(Collection<UUID> partyIds){
        return jpa.findAllById(partyIds);
    }

    @Override
    public boolean reserveSeat(UUID partyId, Instant now){
        return jpa.reserveSeat(partyId, PartyStatus.RECRUITING, now) == 1;
    }

    @Override
    public boolean closeRecruiting(UUID partyId, Instant now){
        return jpa.closeRecruiting(partyId, PartyStatus.RECRUITING, PartyStatus.ACTIVE, now) == 1;
    }

    @Override
    public boolean closeRecruitingByOwner(UUID partyId, UUID ownerId, Instant now){
        return jpa.closeRecruitingByOwner(partyId, ownerId, PartyStatus.RECRUITING, PartyStatus.ACTIVE, now) == 1;

    }

    @Override
    public List<Party> findMatchingCandidates(PartyMetric metric, Instant now, int limit){
        return jpa.findMatchingCandidates(metric, PartyVisibility.PUBLIC, PartyStatus.RECRUITING, now,
                PageRequest.of(0, limit));
    }

    @Override
    public List<Party> findRecruitingPastDeadline(Instant now, int limit){
        return jpa.findByStatusAndDeadlineBefore(PartyStatus.RECRUITING, now, PageRequest.of(0, limit));
    }

    @Override
    public List<Party> findActivePastEnd(Instant now, int limit){
        return jpa.findByStatusAndEndsBefore(PartyStatus.ACTIVE, now, PageRequest.of(0, limit));
    }











}
