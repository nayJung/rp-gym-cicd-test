package com.workoutdone.rpgym.game.quest.domain.repo;

import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyQuestMemberRepository {

    Optional<PartyQuestMember> findByPartyQuestIdAndUserId(UUID partyQuestId, UUID userId);

    List<PartyQuestMember> findByPartyQuestId(UUID partyQuestId);

    PartyQuestMember save(PartyQuestMember member);

    List<PartyQuestMember> saveAll(List<PartyQuestMember> members);
}
