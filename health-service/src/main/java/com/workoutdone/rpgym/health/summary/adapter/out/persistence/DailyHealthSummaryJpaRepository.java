package com.workoutdone.rpgym.health.summary.adapter.out.persistence;

import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyHealthSummaryJpaRepository extends JpaRepository<DailyHealthSummary, UUID> {

    Optional<DailyHealthSummary> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    List<DailyHealthSummary> findByUserIdAndActivityDateBetween(UUID userId, LocalDate from, LocalDate to);

    List<DailyHealthSummary> findByActivityDateBetweenAndAchievedAtIsNullAndFailedAtIsNull(
            LocalDate from, LocalDate to, org.springframework.data.domain.Pageable pageable);
}