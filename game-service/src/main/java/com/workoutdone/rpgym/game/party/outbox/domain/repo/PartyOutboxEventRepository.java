package com.workoutdone.rpgym.game.party.outbox.domain.repo;

import com.workoutdone.rpgym.game.party.outbox.domain.PartyOutboxEvent;

import java.util.List;

public interface PartyOutboxEventRepository {

    PartyOutboxEvent save(PartyOutboxEvent event);

    /**
     * PENDING 을 오래된 순으로, 행 잠금(SKIP LOCKED)을 걸고 집는다.
     * 인스턴스가 여럿이어도 같은 행을 두 번 발행하지 않는다. limit 은 int — Pageable 이 도메인으로 새지 않게.
     */
    List<PartyOutboxEvent> findPendingForUpdate(int limit);
}
