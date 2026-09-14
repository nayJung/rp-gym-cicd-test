package com.workoutdone.rpgym.user.dailyhealthgoal.adapter.in.web.dto;

import com.workoutdone.rpgym.user.dailyhealthgoal.application.GetDailyHealthGoalResult;
import com.workoutdone.rpgym.user.dailyhealthgoal.application.RegisterDailyHealthGoalResult;
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
public class ResDailyHealthGoalDto {

    private UUID id;
    private Integer stepGoal;
    private Integer activeMinutesGoal;
    private Integer activeCaloriesGoal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ResDailyHealthGoalDto from(RegisterDailyHealthGoalResult result) {
        return ResDailyHealthGoalDto.builder()
                .id(result.getId())
                .stepGoal(result.getStepGoal())
                .activeMinutesGoal(result.getActiveMinutesGoal())
                .activeCaloriesGoal(result.getActiveCaloriesGoal())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }

    public static ResDailyHealthGoalDto from(GetDailyHealthGoalResult result) {
        return ResDailyHealthGoalDto.builder()
                .id(result.getId())
                .stepGoal(result.getStepGoal())
                .activeMinutesGoal(result.getActiveMinutesGoal())
                .activeCaloriesGoal(result.getActiveCaloriesGoal())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }
}
