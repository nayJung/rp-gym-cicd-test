package com.workoutdone.rpgym.game.party.outbox.domain;

import com.workoutdone.rpgym.common.entity.BaseCreatedEntity;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 파티 전용 Outbox 행. quest 의 OutboxEvent 와 구조가 같고 테이블만 다르다.
 *
 * 왜 따로 두나 — outbox_events 는 quest 가 소유하고 enum · CHECK 제약으로 자기 이벤트만 허용한다.
 * 파티 이벤트를 거기 얹으려면 quest 파일을 고쳐야 하므로, 파티는 자기 테이블 · 자기 릴레이를 가진다.
 * 발행 토픽은 같은 game.events 이고 소비 측은 eventType 헤더로만 분기하므로 밖에서는 차이가 없다.
 */
@Entity
@Getter
@Table(name = "party_outbox_events", schema = "game_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyOutboxEvent extends BaseCreatedEntity {

    @Id
    @Column(name = "outbox_id", nullable = false, updatable = false)
    private UUID outboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 30, updatable = false)
    private PartyAggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private PartyEventType eventType;

    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    /** Kafka 파티션 키 = userId. 같은 유저의 이벤트는 같은 파티션에서 순서대로 소비된다. */
    @Column(name = "partition_key", nullable = false, length = 50, updatable = false)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PartyOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static PartyOutboxEvent pending(
            UUID outboxId,
            PartyAggregateType aggregateType,
            UUID aggregateId,
            PartyEventType eventType,
            UUID eventId,
            UUID userId,
            String payload
    ) {
        PartyOutboxEvent event = new PartyOutboxEvent();
        event.outboxId = outboxId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.eventId = eventId;
        event.partitionKey = userId.toString();
        event.payload = payload;
        event.status = PartyOutboxStatus.PENDING;
        event.retryCount = 0;
        return event;
    }

    public void markPublished(Instant publishedAt) {
        this.status = PartyOutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    /** 발행 실패. 시도 횟수만 올리고 PENDING 으로 남긴다 — 브로커가 살아나면 다음 폴링이 알아서 집는다. */
    public void markRetried() {
        this.retryCount++;
    }
}
