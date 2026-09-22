package com.workoutdone.rpgym.game.quest.adapter.out.persistence;

import com.workoutdone.rpgym.game.quest.domain.aggregate.QuestSuggestion;
import com.workoutdone.rpgym.game.quest.domain.repo.QuestSuggestionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class QuestSuggestionRepositoryImpl implements QuestSuggestionRepository {

    private final QuestSuggestionJpaRepository questSuggestionJpaRepository;

    @Override
    public Optional<QuestSuggestion> findById(UUID suggestionId) {
        return questSuggestionJpaRepository.findById(suggestionId);
    }

    @Override
    public QuestSuggestion save(QuestSuggestion suggestion) {
        return questSuggestionJpaRepository.save(suggestion);
    }

    // 기본키 조회라 Spring Data 가 이미 주는 existsById 를 그대로 쓴다.
    // 제안 식별자가 곧 기본키이기 때문에 별도 인덱스도 필요 없다.
    @Override
    public boolean existsBySuggestionId(UUID suggestionId) {
        return questSuggestionJpaRepository.existsById(suggestionId);
    }
}
