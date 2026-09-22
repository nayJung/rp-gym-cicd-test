package com.workoutdone.rpgym.game.quest.domain.repo;

import com.workoutdone.rpgym.game.quest.domain.aggregate.QuestSuggestion;

import java.util.Optional;
import java.util.UUID;

// 도메인이 보는 포트다. Spring Data 타입이 이 인터페이스에 나타나지 않는다.
// 구현은 어댑터의 위임 클래스가 맡고, 애플리케이션 계층은 이 인터페이스만 주입받는다.
public interface QuestSuggestionRepository {

    Optional<QuestSuggestion> findById(UUID suggestionId);

    QuestSuggestion save(QuestSuggestion suggestion);

    // 같은 제안이 두 번 배달됐는지 확인한다.
    // 이름을 existsById 로 두지 않은 이유는 도메인이 쓰는 말로 남기기 위해서다.
    boolean existsBySuggestionId(UUID suggestionId);
}
