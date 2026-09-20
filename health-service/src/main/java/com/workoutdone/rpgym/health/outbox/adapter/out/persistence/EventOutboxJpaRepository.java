package com.workoutdone.rpgym.health.outbox.adapter.out.persistence;

import com.workoutdone.rpgym.health.outbox.domain.EventOutbox;
import com.workoutdone.rpgym.health.outbox.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;

public interface EventOutboxJpaRepository extends JpaRepository<EventOutbox, UUID> {

    boolean existsByDedupKey(String dedupKey);

    /**
     * idx_health_activity_outbox_status (status, created_at) 사용.
     *
     * PESSIMISTIC_WRITE → SELECT ... FOR UPDATE
     * lock.timeout = -2 → SKIP LOCKED (Hibernate LockOptions.SKIP_LOCKED)
     * public static final int NO_WAIT      =  0;   // 잠겨 있으면 즉시 에러
     * public static final int WAIT_FOREVER = -1;   // 무한정 기다림
     * public static final int SKIP_LOCKED  = -2;   // 잠긴 건 건너뛰기
     * 다른 인스턴스가 이미 집어간 행은 기다리지 않고 건너뛴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    // seq가 삽입 순서를 그대로 반영하므로 단독 정렬 키로 충분하다.
    // created_at은 같은 트랜잭션에서 동점이 되므로 정렬에 쓰지 않는다.
    @Query("select o from EventOutbox o where o.status = :status order by o.seq asc")
    List<EventOutbox> findByStatusForUpdate(@Param("status") OutboxStatus status, Pageable pageable);

    /**
     * 정리 대상 식별자 조회.
     * idx_health_activity_outbox_cleanup (published_at) WHERE status='PUBLISHED' 사용.
     */
    @Query("""
            select o.outboxId
              from EventOutbox o
             where o.status = :status
               and o.publishedAt < :publishedBefore
             order by o.publishedAt asc
            """)
    List<UUID> findCleanupTargets(@Param("status") OutboxStatus status,
                                  @Param("publishedBefore") LocalDateTime publishedBefore,
                                  Pageable pageable);

    /**
     * 벌크 삭제.
     *
     * JPQL이 곧바로 SQL로 나가므로 영속성 컨텍스트(1차 캐시)를 거치지 않는다.
     * 그대로 두면 DB에서는 지워졌는데 캐시에는 남아 있는 상태가 될 수 있다.
     *
     * 현재 호출부는 식별자만 조회해 넘기므로 캐시에 올라간 엔티티가 없지만,
     * 나중에 엔티티를 로드하는 방식으로 바뀌어도 안전하도록 켜둔다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventOutbox o where o.outboxId in :outboxIds")
    int deleteByOutboxIdIn(@Param("outboxIds") List<UUID> outboxIds);

    long countByStatus(OutboxStatus status);
}