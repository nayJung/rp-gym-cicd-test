package com.workoutdone.rpgym.health.outbox.application;

import com.workoutdone.rpgym.health.outbox.domain.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 정리 배치의 한 묶음.
 *
 * 전체를 한 트랜잭션으로 묶지 않는다.
 * 수만 건을 하나의 DELETE로 처리하면 락 점유 시간이 길어져
 * 같은 테이블을 1초마다 폴링하는 OutboxRelay가 밀린다.
 */
@Service
@RequiredArgsConstructor
public class OutboxCleanupService {

    private final EventOutboxRepository eventOutboxRepository;

    /** @return 이번 묶음에서 삭제된 건수 */
    @Transactional
    public int deleteOnce(LocalDateTime publishedBefore, int batchSize) {
        List<UUID> targets = eventOutboxRepository.findCleanupTargets(publishedBefore, batchSize);

        if (targets.isEmpty()) {
            return 0;
        }

        return eventOutboxRepository.deleteByOutboxIds(targets);
    }
}