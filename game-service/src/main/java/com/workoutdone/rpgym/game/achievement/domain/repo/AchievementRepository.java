package com.workoutdone.rpgym.game.achievement.domain.repo;

import com.workoutdone.rpgym.game.achievement.domain.AchievementScope;
import com.workoutdone.rpgym.game.achievement.domain.aggregate.Achievement;

import java.util.List;

public interface AchievementRepository {

    /** 지금 세야 하는 업적. status = ACTIVE */
    List<Achievement> findActiveByScope(AchievementScope scope);

    /** 목록 화면용. INACTIVE 도 포함 -- 이미 딴 기록은 보여야 한다. sort_order 는 V6 에 없어 code 순 */
    List<Achievement> findAllByScope(AchievementScope scope);
}
