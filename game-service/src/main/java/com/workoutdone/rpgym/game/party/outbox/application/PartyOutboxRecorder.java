package com.workoutdone.rpgym.game.party.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.outbox.domain.PartyOutboxEvent;
import com.workoutdone.rpgym.game.party.outbox.domain.repo.PartyOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 파티 이벤트를 party_outbox_events 에 적재한다. 호출자의 트랜잭션에 합류하므로
 * 파티 상태 변경과 이벤트 적재가 함께 커밋되거나 함께 롤백된다. 발행은 PartyOutboxRelay 가 따로 한다.
 */
@Component
@RequiredArgsConstructor
public class PartyOutboxRecorder {

    private final PartyOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public PartyOutboxEvent append(
            PartyAggregateType aggregateType,
            UUID aggregateId,
            PartyEventType eventType,
            UUID userId,
            Instant occurredAt,
            Object data
    ) {
        UUID eventId = UUID.randomUUID();
        PartyEventEnvelope envelope = new PartyEventEnvelope(eventId, eventType.name(), occurredAt, userId, data);

        return repository.save(PartyOutboxEvent.pending(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                eventId,
                userId,
                toJson(envelope)
        ));
    }

    private String toJson(PartyEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize " + envelope.eventType(), e);
        }
    }
}
