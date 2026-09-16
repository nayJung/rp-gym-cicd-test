package com.workoutdone.rpgym.health.summary.adapter.out.persistence;

import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummary;
import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;

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
    public List<DailyHealthSummary> findUnresolvedBetween(LocalDate from, LocalDate to, int limit) {
        return jpaRepository.findByActivityDateBetweenAndAchievedAtIsNullAndFailedAtIsNull(
                from, to, PageRequest.of(0, limit));
    }

    @Override
    public Optional<DailyHealthSummary> findById(UUID summaryId) {
        return jpaRepository.findById(summaryId);
    }
}