package com.workoutdone.rpgym.user.dailyhealthgoal.application;

import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetDailyHealthGoalResult {

    private UUID id;
    private Integer stepGoal;
    private Integer activeMinutesGoal;
    private Integer activeCaloriesGoal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GetDailyHealthGoalResult from(DailyHealthGoal dailyHealthGoal) {
        return GetDailyHealthGoalResult.builder()
                .id(dailyHealthGoal.getId())
                .stepGoal(dailyHealthGoal.getStepGoal())
                .activeMinutesGoal(dailyHealthGoal.getActiveMinutesGoal())
                .activeCaloriesGoal(dailyHealthGoal.getActiveCaloriesGoal())
                .createdAt(dailyHealthGoal.getCreatedAt())
                .updatedAt(dailyHealthGoal.getUpdatedAt())
                .build();
    }
}
