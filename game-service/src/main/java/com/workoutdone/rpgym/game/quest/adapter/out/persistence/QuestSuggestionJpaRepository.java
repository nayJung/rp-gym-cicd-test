package com.workoutdone.rpgym.game.quest.adapter.out.persistence;

import com.workoutdone.rpgym.game.quest.domain.aggregate.QuestSuggestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuestSuggestionJpaRepository extends JpaRepository<QuestSuggestion, UUID> {
}
