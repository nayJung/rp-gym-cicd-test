package com.workoutdone.rpgym.game.quest.adapter.out.persistence;

import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;
import com.workoutdone.rpgym.game.quest.domain.repo.PartyQuestMemberRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PartyQuestMemberRepositoryImpl implements PartyQuestMemberRepository {

    private final PartyQuestMemberJpaRepository partyQuestMemberJpaRepository;

    @Override
    public Optional<PartyQuestMember> findByPartyQuestIdAndUserId(UUID partyQuestId, UUID userId) {
        return partyQuestMemberJpaRepository.findByPartyQuestIdAndUserId(partyQuestId, userId);
    }

    @Override
    public List<PartyQuestMember> findByPartyQuestId(UUID partyQuestId) {
        return partyQuestMemberJpaRepository.findByPartyQuestId(partyQuestId);
    }

    @Override
    public PartyQuestMember save(PartyQuestMember member) {
        return partyQuestMemberJpaRepository.save(member);
    }

    @Override
    public List<PartyQuestMember> saveAll(List<PartyQuestMember> members) {
        return partyQuestMemberJpaRepository.saveAll(members);
    }
}
