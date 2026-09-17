package com.workoutdone.rpgym.health.outbox.application;

import com.workoutdone.rpgym.health.outbox.config.OutboxCleanupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 발행 완료된 Outbox 행 정리 스케줄러.
 *
 * batchSize 단위로 끊어 반복 삭제하고, maxRunDuration이 지나면 멈춘다.
 * 상한에 걸려 남은 대상은 다음 주기가 이어서 처리한다.
 *
 * 정리 대상은 PUBLISHED이고 발행기는 PENDING만 잠그므로 대상이 겹치지 않는다.
 * 따라서 SKIP LOCKED는 필요하지 않다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "rpgym.outbox.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxCleanupScheduler {

    private final OutboxCleanupService outboxCleanupService;
    private final OutboxMetrics outboxMetrics;
    private final OutboxCleanupProperties properties;

    @Scheduled(cron = "${rpgym.outbox.cleanup.cron:0 10 4 * * *}", zone = "Asia/Seoul")
    public void cleanupPublishedEvents() {
        LocalDateTime publishedBefore = LocalDateTime.now().minus(properties.retention());

        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + properties.maxRunDuration().toNanos();

        int totalDeleted = 0;
        boolean reachedTimeLimit = false;

        while (true) {
            if (System.nanoTime() >= deadlineNanos) {
                reachedTimeLimit = true;
                break;
            }

            int deleted;
            try {
                deleted = outboxCleanupService.deleteOnce(publishedBefore, properties.batchSize());
            } catch (Exception e) {
                /*
                 * 묶음 단위 트랜잭션이라 실패해도 앞서 삭제한 건은 그대로 남는다.
                 * 여기서 흡수하지 않으면 지금까지 얼마나 지웠는지가 로그에 남지 않는다.
                 */
                log.error("Outbox 정리 배치 중 오류가 발생해 이번 실행을 중단한다. 삭제={}", totalDeleted, e);
                return;
            }

            if (deleted == 0) {
                break;
            }

            totalDeleted += deleted;
            outboxMetrics.recordCleanupDeleted(deleted);
        }

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        if (reachedTimeLimit) {
            log.warn("1회 실행 시간 상한에 도달했다. 남은 대상은 다음 주기에 처리한다. 삭제={} 상한={} 소요={}ms",
                    totalDeleted, properties.maxRunDuration(), elapsedMillis);
            return;
        }

        if (totalDeleted > 0) {
            log.info("Outbox 정리 배치를 완료했다. 삭제={} 기준시각={} 소요={}ms",
                    totalDeleted, publishedBefore, elapsedMillis);
        }
    }
}
