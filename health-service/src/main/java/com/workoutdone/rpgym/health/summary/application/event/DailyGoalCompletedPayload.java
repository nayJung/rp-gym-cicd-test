package com.workoutdone.rpgym.health.summary.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyGoalCompletedPayload(
        UUID summaryId,
        LocalDate activityDate,
        Instant achievedAt
) {
}