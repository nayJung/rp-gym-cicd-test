package com.workoutdone.rpgym.health.summary.adapter.out.persistence;

import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummary;
import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DailyHealthSummaryRepositoryImpl implements DailyHealthSummaryRepository {

    private final DailyHealthSummaryJpaRepository jpaRepository;

    @Override
    public DailyHealthSummary save(DailyHealthSummary summary) {
        return jpaRepository.save(summary);
    }

    @Override
    public Optional<DailyHealthSummary> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate) {
        return jpaRepository.findByUserIdAndActivityDate(userId, activityDate);
    }

    @Override
    public List<DailyHealthSummary> findByUserIdAndActivityDateBetween(UUID userId, LocalDate from, LocalDate to) {
        return jpaRepository.findByUserIdAndActivityDateBetween(userId, from, to);
    }

    @Override
    public List<DailyHealthSummary> findUnresolvedBefore(LocalDate date) {
        return jpaRepository.findByActivityDateBeforeAndAchievedAtIsNullAndFailedAtIsNull(date);
    }
}