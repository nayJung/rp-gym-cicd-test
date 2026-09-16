package com.workoutdone.rpgym.health.summary.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyHealthSummaryRepository {

    DailyHealthSummary save(DailyHealthSummary summary);

    Optional<DailyHealthSummary> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    List<DailyHealthSummary> findByUserIdAndActivityDateBetween(UUID userId, LocalDate from, LocalDate to);

    List<DailyHealthSummary> findUnresolvedBetween(LocalDate from, LocalDate to, int limit);

    Optional<DailyHealthSummary> findById(UUID summaryId);
}