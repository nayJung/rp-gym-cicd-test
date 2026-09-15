package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

//@Repository
//@RequiredArgsConstructor
//public class PartyRepositoryImpl implements PartyRepository {
//
//    private final PartyJpaRepository jpa;
//
//    @Override
//    public Party save(Party party) {
//        return jpa.save(party);
//    }
//
//
//    @Override
//    public Optional<Party> findById(UUID partyId) {
//        return jpa.findById(partyId);
//    }
//
//
//    @Override
//    public Optional<Party> findByIdForUpdate(UUID partyId) {
//        return jpa.findByIdForUpdate(partyId);
//    }
//}
