package com.workoutdone.rpgym.user.dailyhealthgoal.domain;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedDeletedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_daily_health_goals", schema = "user_service")
public class DailyHealthGoal extends BaseCreatedUpdatedDeletedEntity {

    public static final int DEFAULT_STEP_GOAL = 5000;
    public static final int DEFAULT_ACTIVE_MINUTES_GOAL = 60;
    public static final int DEFAULT_ACTIVE_CALORIES_GOAL = 300;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "step_goal", nullable = false)
    private Integer stepGoal;

    @Column(name = "active_minutes_goal", nullable = false)
    private Integer activeMinutesGoal;

    @Column(name = "active_calories_goal", nullable = false)
    private Integer activeCaloriesGoal;

    private DailyHealthGoal(UUID userId, Integer stepGoal, Integer activeMinutesGoal, Integer activeCaloriesGoal) {
        this.userId = userId;
        this.stepGoal = stepGoal;
        this.activeMinutesGoal = activeMinutesGoal;
        this.activeCaloriesGoal = activeCaloriesGoal;
    }

    // 요청에서 생략한(null) 목표값은 기본값으로 채움
    public static DailyHealthGoal create(UUID userId, Integer stepGoal, Integer activeMinutesGoal, Integer activeCaloriesGoal) {
        return new DailyHealthGoal(
                userId,
                stepGoal != null ? stepGoal : DEFAULT_STEP_GOAL,
                activeMinutesGoal != null ? activeMinutesGoal : DEFAULT_ACTIVE_MINUTES_GOAL,
                activeCaloriesGoal != null ? activeCaloriesGoal : DEFAULT_ACTIVE_CALORIES_GOAL
        );
    }
}
