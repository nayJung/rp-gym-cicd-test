package com.workoutdone.rpgym.health.summary.adapter.in.scheduler;

import com.workoutdone.rpgym.health.summary.application.DailyGoalFailureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 일일 목표 실패 처리 스케줄러 (Driving Adapter).
 *
 * 사용자가 하루 동안 앱을 켜지 않아 동기화 자체가 없으면
 * "미달성"을 판정해 줄 트리거가 없다. 그래서 매일 자정 직후
 * 전날까지의 미해결(성공도 실패도 아닌) 기록을 일괄 실패 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyGoalFailureScheduler {

    private final DailyGoalFailureService dailyGoalFailureService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void markYesterdayUnresolvedAsFailed() {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            int failedCount = dailyGoalFailureService.markUnresolvedAsFailed(today, Instant.now());

            if (failedCount > 0) {
                log.info("일일 목표 실패 처리를 완료했다. count={}", failedCount);
            }
        } catch (Exception e) {
            log.error("일일 목표 실패 처리 배치 중 예기치 못한 오류가 발생했다.", e);
        }
    }
}