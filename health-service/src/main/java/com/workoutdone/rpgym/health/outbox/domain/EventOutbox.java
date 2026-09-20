package com.workoutdone.rpgym.health.outbox.domain;

import com.workoutdone.rpgym.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox (애그리거트 루트).
 *
 * Health Service가 발행하는 모든 이벤트는 저장 트랜잭션 안에서 이 테이블에 함께 기록되고,
 * 별도 발행기가 PENDING 행을 읽어 Kafka로 보낸다. (발행기는 후속 이슈)
 *
 * eventId는 payload 본문의 eventId와 동일한 값이며, 재전송에도 절대 변하지 않는다.
 * Game Service가 이 값을 퀘스트 중복 생성 방지 키로 사용한다. (팀 합의)
 *
 * HealthActivity와는 별개의 애그리거트이므로 객체 참조 대신 sourceActivityId(UUID)로 참조한다.
 */
@Getter
@Entity
@Table(
        name = "event_outbox",
        schema = "health_service",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_health_activity_outbox_dedup",
                        columnNames = "dedup_key"),
                @UniqueConstraint(name = "uk_health_activity_outbox_event_id",
                        columnNames = "event_id")
        },
        indexes = {
                // 발행 폴링용 — PENDING을 seq 순서로 조회
                @Index(name = "idx_health_activity_outbox_status",
                        columnList = "status, seq"),
                // 정리 배치용 — PUBLISHED를 published_at 기준으로 조회
                @Index(name = "idx_health_activity_outbox_cleanup",
                        columnList = "status, published_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOutbox extends BaseCreatedEntity {

    @Id
    @Column(name = "outbox_id", nullable = false, updatable = false)
    private UUID outboxId;

    /** 이벤트 본문의 eventId와 동일. 재전송 시에도 불변. */
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false, updatable = false)
    private HealthEventType eventType;

    /** 이 이벤트를 유발한 동기화 건 (MSA 분리로 물리 FK 미설정, 논리 참조) */
    @Column(name = "source_activity_id", nullable = false, updatable = false)
    private UUID sourceActivityId;

    @Column(name = "dedup_key", length = 150, nullable = false, updatable = false)
    private String dedupKey;

    /** Kafka 파티션 키 = userId. 같은 사용자 이벤트의 순서를 보장한다. */
    @Column(name = "partition_key", length = 50, nullable = false, updatable = false)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** 우리 서버가 Kafka로 내보낸 시각 (운영 기록). 판정에 쓰지 않는다. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * 낙관적 락.
     *
     * HealthActivity와 동일하게 신규 판단 근거가 되어 save()가 persist()를 타게 한다.
     * 더불어 발행기가 다중 인스턴스로 늘어났을 때 같은 PENDING 행을 두 인스턴스가 집어
     * 중복 발행하는 것을 막는다. (FOR UPDATE SKIP LOCKED 도입 전까지의 안전장치)
     *
     * ※ 절대 직접 대입하지 않는다. Hibernate가 persist 시점에 채운다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 발행 순서 결정용 단조 증가 시퀀스.
     * DB가 IDENTITY로 채우므로 애플리케이션에서 직접 대입하지 않는다.
     * created_at은 같은 트랜잭션의 행들이 동일한 값을 가지므로 정렬 키로 쓸 수 없다.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    private EventOutbox(UUID outboxId, UUID eventId, HealthEventType eventType,
                        UUID sourceActivityId, String dedupKey,
                        String partitionKey, String payload) {
        this.outboxId = outboxId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.sourceActivityId = sourceActivityId;
        this.dedupKey = dedupKey;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public static EventOutbox pending(UUID outboxId, UUID eventId, HealthEventType eventType,
                                      UUID sourceActivityId, String dedupKey,
                                      String partitionKey, String payload) {
        return new EventOutbox(outboxId, eventId, eventType,
                sourceActivityId, dedupKey, partitionKey, payload);
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
    }

    /** 재시도 대상으로 되돌린다 */
    public void markRetryable() {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
    }
}