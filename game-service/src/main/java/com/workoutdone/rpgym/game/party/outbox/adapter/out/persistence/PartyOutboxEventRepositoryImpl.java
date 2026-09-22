package com.workoutdone.rpgym.game.party.outbox.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.outbox.domain.PartyOutboxEvent;
import com.workoutdone.rpgym.game.party.outbox.domain.PartyOutboxStatus;
import com.workoutdone.rpgym.game.party.outbox.domain.repo.PartyOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PartyOutboxEventRepositoryImpl implements PartyOutboxEventRepository {

    private final PartyOutboxEventJpaRepository jpa;

    @Override
    public PartyOutboxEvent save(PartyOutboxEvent event) {
        return jpa.save(event);
    }

    @Override
    public List<PartyOutboxEvent> findPendingForUpdate(int limit) {
        return jpa.findByStatusForUpdate(PartyOutboxStatus.PENDING, PageRequest.of(0, limit));
    }
}
