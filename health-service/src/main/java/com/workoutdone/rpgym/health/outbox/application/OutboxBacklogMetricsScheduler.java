package com.workoutdone.rpgym.health.outbox.application;

import com.workoutdone.rpgym.health.outbox.domain.EventOutboxRepository;
import com.workoutdone.rpgym.health.outbox.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적체 지표(PENDING / FAILED 건수) 갱신.
 *
 * 발행 폴링은 1초 주기라 매 라운드마다 count 쿼리를 돌리면 낭비다.
 * 알림 임계치 판단에 필요한 정밀도는 30초면 충분하므로 별도 주기로 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxBacklogMetricsScheduler {

    private final EventOutboxRepository eventOutboxRepository;
    private final OutboxMetrics outboxMetrics;

    @Scheduled(fixedDelayString = "${rpgym.outbox.backlog-refresh-interval:30000}")
    @Transactional(readOnly = true)
    public void refreshBacklogMetrics() {
        try {
            outboxMetrics.updateBacklog(
                    eventOutboxRepository.countByStatus(OutboxStatus.PENDING),
                    eventOutboxRepository.countByStatus(OutboxStatus.FAILED)
            );
        } catch (Exception e) {
            log.warn("Outbox 적체 지표 갱신에 실패했다. 다음 주기에 다시 시도한다.", e);
        }
    }
}