package com.workoutdone.rpgym.health.outbox.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

/**
 * 포트 — 순수 자바 인터페이스.
 * Health Summary 도메인도 이 컨텍스트를 통해 이벤트를 기록한다.
 */
public interface EventOutboxRepository {

    EventOutbox save(EventOutbox eventOutbox);

    /** 이벤트 중복 발행 방지 */
    boolean existsByDedupKey(String dedupKey);

    /**
     * 미발행(PENDING) 이벤트를 오래된 순으로 조회하며 행 잠금을 건다.
     *
     * 발행기가 다중 인스턴스로 늘어나도 같은 행을 두 인스턴스가 집어
     * 중복 발행하는 것을 막는다. 이미 잠긴 행은 기다리지 않고 건너뛴다(SKIP LOCKED).
     */
    List<EventOutbox> findPendingForUpdate(int limit);

    /**
     * 정리 대상(PUBLISHED이면서 보관 기간이 지난) 행의 식별자를 오래된 순으로 조회한다.
     *
     * 엔티티가 아니라 식별자만 가져오는 이유는 삭제에 본문이 필요 없기 때문이다.
     * payload가 jsonb라 전체를 로드하면 메모리 낭비가 크다.
     */
    List<UUID> findCleanupTargets(LocalDateTime publishedBefore, int limit);

    /** 식별자 목록으로 일괄 삭제한다. @return 실제 삭제된 건수 */
    int deleteByOutboxIds(List<UUID> outboxIds);

    /** 적체 감시 지표용 상태별 건수 */
    long countByStatus(OutboxStatus status);
}