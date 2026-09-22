package com.workoutdone.rpgym.game.achievement.domain.aggregate;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedEntity;
import com.workoutdone.rpgym.game.achievement.domain.ConditionType;
import com.workoutdone.rpgym.game.achievement.domain.CountResult;
import com.workoutdone.rpgym.game.achievement.domain.OwnerType;
import com.workoutdone.rpgym.game.achievement.domain.UserAchievementStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * owner(유저 or 파티) 의 업적 1개에 대한 진행 카운터이자 획득 기록.
 * (owner_type, owner_id, achievement_id) 당 1행 -- uk_user_achievements_owner_achievement.
 *
 * 멱등의 핵심은 last_counted_date 다. Health 가 같은 날 DAILY_GOAL_COMPLETED 를 두 번 보내도
 * (재전송, 컨슈머 재시도) 두 번째는 SKIPPED 로 끝난다. dedupKey 는 Health 쪽 방어고 이건 내 쪽 방어다.
 *
 * 과거 날짜(activityDate < last_counted_date) 도 SKIPPED 다. 순서가 뒤집힌 이벤트를 세면
 * STREAK 가 1로 리셋되어 이미 쌓은 연속을 잃는다. 하루 1건 규칙 아래에서 과거 날짜는
 * 이미 셌거나(중복) 놓친 날(재전송 불가) 둘 중 하나라 세지 않는 편이 안전하다.
 */
@Entity
@Getter
@Table(name = "user_achievements", schema = "game_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAchievement extends BaseCreatedUpdatedEntity {

    @Id
    @Column(name = "user_achievement_id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 10, updatable = false)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "achievement_id", nullable = false, updatable = false)
    private UUID achievementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserAchievementStatus status;

    @Column(name = "current_value", nullable = false)
    private int currentValue;

    @Column(name = "last_counted_date")
    private LocalDate lastCountedDate;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static UserAchievement start(UUID id, OwnerType ownerType, UUID ownerId, UUID achievementId) {
        UserAchievement ua = new UserAchievement();
        ua.id = id;
        ua.ownerType = ownerType;
        ua.ownerId = ownerId;
        ua.achievementId = achievementId;
        ua.status = UserAchievementStatus.IN_PROGRESS;
        ua.currentValue = 0;
        return ua;
    }

    /**
     * activityDate 하루치를 센다.
     *
     * @param conditionType  세는 방식 (누적 / 연속)
     * @param conditionValue 이 값에 닿으면 ACHIEVED
     * @param achievedAt     Health 가 준 달성 시각. 내 서버 시계가 아니라 이 값을 기록해야
     *                       재처리해도 같은 값이 남는다
     */
    public CountResult count(LocalDate activityDate, ConditionType conditionType, int conditionValue,
                             Instant achievedAt) {
        if (status == UserAchievementStatus.ACHIEVED) {
            return CountResult.SKIPPED;
        }
        if (lastCountedDate != null && !activityDate.isAfter(lastCountedDate)) {
            return CountResult.SKIPPED;
        }

        currentValue = switch (conditionType) {
            case DAILY_GOAL_COUNT -> currentValue + 1;
            case DAILY_GOAL_STREAK -> isConsecutive(activityDate) ? currentValue + 1 : 1;
        };
        lastCountedDate = activityDate;

        if (currentValue >= conditionValue) {
            status = UserAchievementStatus.ACHIEVED;
            this.achievedAt = achievedAt;
            return CountResult.ACHIEVED;
        }
        return CountResult.PROGRESSED;
    }

    private boolean isConsecutive(LocalDate activityDate) {
        return lastCountedDate != null && lastCountedDate.plusDays(1).equals(activityDate);
    }

    public boolean isAchieved() {
        return status == UserAchievementStatus.ACHIEVED;
    }
}
