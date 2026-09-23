package com.workoutdone.rpgym.game.quest.adapter.out.persistence;

import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyQuestMemberJpaRepository extends JpaRepository<PartyQuestMember, UUID> {

    Optional<PartyQuestMember> findByPartyQuestIdAndUserId(UUID partyQuestId, UUID userId);

    List<PartyQuestMember> findByPartyQuestId(UUID partyQuestId);
}
