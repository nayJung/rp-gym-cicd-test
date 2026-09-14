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
}