package com.workoutdone.rpgym.game.party.outbox.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.outbox.domain.PartyOutboxEvent;
import com.workoutdone.rpgym.game.party.outbox.domain.PartyOutboxStatus;
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

public interface PartyOutboxEventJpaRepository extends JpaRepository<PartyOutboxEvent, UUID> {

    /**
     * idx_party_outbox_events_status (status, created_at) 를 탄다.
     * PESSIMISTIC_WRITE + lock.timeout=-2 → SELECT ... FOR UPDATE SKIP LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from PartyOutboxEvent o where o.status = :status order by o.createdAt asc")
    List<PartyOutboxEvent> findByStatusForUpdate(@Param("status") PartyOutboxStatus status, Pageable pageable);
}
