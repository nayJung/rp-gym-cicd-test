package com.workoutdone.rpgym.health.summary.domain;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.time.Duration;


@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "daily_health_summaries",
        schema = "health_service",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_daily_health_summaries_user_date",
                columnNames = {"user_id", "activity_date"}
        )
)
public class DailyHealthSummary extends BaseCreatedUpdatedEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "summary_id", updatable = false, nullable = false)
    private UUID summaryId;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "activity_date", updatable = false, nullable = false)
    private LocalDate activityDate;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps = 0;

    @Column(name = "total_active_minutes", nullable = false)
    private Integer totalActiveMinutes = 0;

    @Column(name = "total_active_calories", nullable = false)
    private Integer totalActiveCalories = 0;

    @Column(name = "is_all_goals_achieved", nullable = false)
    private boolean allGoalsAchieved = false;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "quest_suggested_at")
    private Instant questSuggestedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private DailyHealthSummary(UUID userId, LocalDate activityDate, Instant now) {
        this.userId = userId;
        this.activityDate = activityDate;
        this.calculatedAt = now;
    }

    public static DailyHealthSummary createFor(UUID userId, LocalDate activityDate, Instant now) {
        return new DailyHealthSummary(userId, activityDate, now);
    }

    public boolean applySync(int steps, int activeMinutes, int activeCalories, Instant measuredAt, Instant calculatedAt) {
        if (this.lastSyncedAt != null && measuredAt.isBefore(this.lastSyncedAt)) {
            return false;
        }
        if (measuredAt.equals(this.lastSyncedAt) && hasSameSnapshot(steps, activeMinutes, activeCalories)) {
            return false;
        }
        this.totalSteps = steps;
        this.totalActiveMinutes = activeMinutes;
        this.totalActiveCalories = activeCalories;
        this.lastSyncedAt = measuredAt;
        this.calculatedAt = calculatedAt;
        return true;
    }

    public boolean markAllGoalsAchieved(Instant now) {
        if (this.achievedAt != null || this.failedAt != null) {
            return false;
        }
        this.allGoalsAchieved = true;
        this.achievedAt = now;
        return true;
    }

    public boolean markAsFailed(Instant now) {
        if (this.achievedAt != null || this.failedAt != null) {
            return false;
        }
        this.failedAt = now;
        return true;
    }

    public boolean isQuestSuggestionDue(Instant now, Duration suggestionInterval) {
        return this.questSuggestedAt == null
                || !now.isBefore(this.questSuggestedAt.plus(suggestionInterval));
    }

    public void recordQuestSuggested(Instant now) {
        this.questSuggestedAt = now;
    }

    private boolean hasSameSnapshot(int steps, int activeMinutes, int activeCalories) {
        return this.totalSteps == steps
                && this.totalActiveMinutes == activeMinutes
                && this.totalActiveCalories == activeCalories;
    }
}