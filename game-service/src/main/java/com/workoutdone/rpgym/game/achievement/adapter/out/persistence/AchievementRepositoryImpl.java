package com.workoutdone.rpgym.game.achievement.adapter.out.persistence;

import com.workoutdone.rpgym.game.achievement.domain.AchievementScope;
import com.workoutdone.rpgym.game.achievement.domain.AchievementStatus;
import com.workoutdone.rpgym.game.achievement.domain.aggregate.Achievement;
import com.workoutdone.rpgym.game.achievement.domain.repo.AchievementRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AchievementRepositoryImpl implements AchievementRepository {

    private final AchievementJpaRepository achievementJpaRepository;

    @Override
    public List<Achievement> findActiveByScope(AchievementScope scope) {
        return achievementJpaRepository.findAllByScopeAndStatusOrderByCodeAsc(scope, AchievementStatus.ACTIVE);
    }

    @Override
    public List<Achievement> findAllByScope(AchievementScope scope) {
        return achievementJpaRepository.findAllByScopeOrderByCodeAsc(scope);
    }
}
