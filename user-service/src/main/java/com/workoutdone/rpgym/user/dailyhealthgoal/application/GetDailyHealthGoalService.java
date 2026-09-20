package com.workoutdone.rpgym.user.dailyhealthgoal.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoal;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoalErrorCode;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDailyHealthGoalService {

    private final DailyHealthGoalRepository dailyHealthGoalRepository;

    public GetDailyHealthGoalResult getDailyHealthGoal(UUID userId) {
        DailyHealthGoal dailyHealthGoal = dailyHealthGoalRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BaseException(DailyHealthGoalErrorCode.DAILY_GOAL_NOT_FOUND));

        return GetDailyHealthGoalResult.from(dailyHealthGoal);
    }
}
