package com.workoutdone.rpgym.health.summary.adapter.in.scheduler;

import com.workoutdone.rpgym.health.summary.application.DailyGoalFailureService;
import com.workoutdone.rpgym.health.summary.domain.DailyHealthSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class DailyGoalFailureScheduler {

    private final DailyGoalFailureService dailyGoalFailureService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void markYesterdayUnresolvedAsFailed() {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            Instant now = Instant.now();
            List<DailyHealthSummary> unresolved = dailyGoalFailureService.findUnresolved(today);

            int failedCount = 0;
            for (DailyHealthSummary summary : unresolved) {
                try {
                    if (dailyGoalFailureService.markSummaryAsFailed(summary.getSummaryId(), now)) {
                        failedCount++;
                    }
                } catch (Exception e) {
                    log.warn("summaryId={} 일일 목표 실패 처리 중 오류, 다음 배치에서 재시도한다.",
                            summary.getSummaryId(), e);
                }
            }

            if (failedCount > 0) {
                log.info("일일 목표 실패 처리를 완료했다. count={}", failedCount);
            }
        } catch (Exception e) {
            log.error("일일 목표 실패 처리 배치 중 예기치 못한 오류가 발생했다.", e);
        }
    }
}