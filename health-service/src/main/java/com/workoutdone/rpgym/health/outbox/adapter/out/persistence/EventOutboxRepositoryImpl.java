package com.workoutdone.rpgym.health.outbox.adapter.out.persistence;

import com.workoutdone.rpgym.health.outbox.domain.EventOutbox;
import com.workoutdone.rpgym.health.outbox.domain.EventOutboxRepository;
import com.workoutdone.rpgym.health.outbox.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.UUID;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EventOutboxRepositoryImpl implements EventOutboxRepository {

    private final EventOutboxJpaRepository eventOutboxJpaRepository;

    @Override
    public EventOutbox save(EventOutbox eventOutbox) {
        return eventOutboxJpaRepository.save(eventOutbox);
    }

    @Override
    public boolean existsByDedupKey(String dedupKey) {
        return eventOutboxJpaRepository.existsByDedupKey(dedupKey);
    }

    @Override
    public List<EventOutbox> findPendingForUpdate(int limit) {
        return eventOutboxJpaRepository.findByStatusForUpdate(
                OutboxStatus.PENDING, PageRequest.of(0, limit));
    }

    @Override
    public List<UUID> findCleanupTargets(LocalDateTime publishedBefore, int limit) {
        return eventOutboxJpaRepository.findCleanupTargets(
                OutboxStatus.PUBLISHED, publishedBefore, PageRequest.of(0, limit));
    }

    @Override
    public int deleteByOutboxIds(List<UUID> outboxIds) {
        if (outboxIds.isEmpty()) {
            return 0;
        }
        return eventOutboxJpaRepository.deleteByOutboxIdIn(outboxIds);
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return eventOutboxJpaRepository.countByStatus(status);
    }
}