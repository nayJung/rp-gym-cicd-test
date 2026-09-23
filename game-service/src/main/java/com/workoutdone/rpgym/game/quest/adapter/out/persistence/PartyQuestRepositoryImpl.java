package com.workoutdone.rpgym.game.quest.adapter.out.persistence;

import com.workoutdone.rpgym.game.quest.domain.QuestStatus;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuest;
import com.workoutdone.rpgym.game.quest.domain.repo.PartyQuestRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PartyQuestRepositoryImpl implements PartyQuestRepository {

    private final PartyQuestJpaRepository partyQuestJpaRepository;

    @Override
    public Optional<PartyQuest> findById(UUID partyQuestId) {
        return partyQuestJpaRepository.findById(partyQuestId);
    }

    @Override
    public PartyQuest save(PartyQuest partyQuest) {
        return partyQuestJpaRepository.save(partyQuest);
    }

    @Override
    public Optional<PartyQuest> findActiveByUserId(UUID userId, Instant at) {
        return partyQuestJpaRepository.findActiveByUserId(userId, QuestStatus.ACTIVE, at)
                .stream()
                .findFirst();
    }

    @Override
    public boolean existsActiveByPartyId(UUID partyId, Instant at) {
        return partyQuestJpaRepository.existsActiveByPartyId(partyId, QuestStatus.ACTIVE, at);
    }

    @Override
    public int addToCurrentValue(UUID partyQuestId, int delta) {
        return partyQuestJpaRepository.addToCurrentValue(partyQuestId, delta);
    }

    @Override
    public int claimCompletion(UUID partyQuestId, Instant at) {
        return partyQuestJpaRepository.claimCompletion(
                partyQuestId, at, QuestStatus.ACTIVE, QuestStatus.COMPLETED);
    }
}
