package com.workoutdone.rpgym.health.summary.application;

import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummary;
import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyGoalFailureService {


    private static final int LOOKBACK_DAYS = 30;
    public static final int BATCH_LIMIT = 500;

    private final DailyHealthSummaryRepository summaryRepository;

    @Transactional(readOnly = true)
    public List<DailyHealthSummary> findUnresolved(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        LocalDate from = today.minusDays(LOOKBACK_DAYS);
        return summaryRepository.findUnresolvedBetween(from, yesterday, BATCH_LIMIT);
    }

    @Transactional
    public boolean markSummaryAsFailed(UUID summaryId, Instant now) {
        return summaryRepository.findById(summaryId)
                .map(summary -> {
                    boolean changed = summary.markAsFailed(now);
                    if (changed) {
                        summaryRepository.save(summary);
                    }
                    return changed;
                })
                .orElse(false);
    }
}