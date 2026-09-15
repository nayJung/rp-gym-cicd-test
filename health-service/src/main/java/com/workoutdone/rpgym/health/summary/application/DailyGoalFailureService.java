package com.workoutdone.rpgym.health.summary.application;

import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummary;
import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyGoalFailureService {

    private final DailyHealthSummaryRepository summaryRepository;

    @Transactional
    public int markUnresolvedAsFailed(LocalDate today, Instant now) {
        List<DailyHealthSummary> unresolved = summaryRepository.findUnresolvedBefore(today);

        int failedCount = 0;
        for (DailyHealthSummary summary : unresolved) {
            if (summary.markAsFailed(now)) {
                summaryRepository.save(summary);
                failedCount++;
            }
        }
        return failedCount;
    }
}