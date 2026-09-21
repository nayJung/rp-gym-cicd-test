package com.workoutdone.rpgym.health.outbox.application;

import com.workoutdone.rpgym.health.outbox.domain.HealthEventType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Outbox 발행 경로의 Micrometer 지표.
 *
 * 태그는 값의 종류가 유한한 것만 쓴다.
 * userId나 eventId를 태그로 넣으면 조합마다 별도 시계열이 생겨
 * Prometheus 인덱스와 JVM 힙이 무한히 커진다. 개별 추적은 로그가 담당한다.
 *
 * 지표 이름은 Micrometer 관례에 따라 점으로 쓰고,
 * Prometheus에서는 rpgym_outbox_publish_total 같은 형태로 노출된다.
 */
@Component
public class OutboxMetrics {

    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_RESULT = "result";

    private final MeterRegistry meterRegistry;

    /** Gauge가 참조하는 값. 강한 참조를 유지해야 수집이 끊기지 않는다 */
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();

    public OutboxMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("rpgym.outbox.pending.count", pendingCount, AtomicLong::get)
                .description("아직 발행되지 않은 Outbox 행 수")
                .register(meterRegistry);

        Gauge.builder("rpgym.outbox.failed.count", failedCount, AtomicLong::get)
                .description("재시도를 소진하고 DLQ로 보낸 Outbox 행 수")
                .register(meterRegistry);
    }

    /**
     * @param publishDuration Kafka ack까지 걸린 시간
     * @param lag             적재(created_at) → 발행(published_at) 지연
     */
    public void recordPublishSuccess(HealthEventType eventType,
                                     Duration publishDuration,
                                     Duration lag) {
        meterRegistry.counter("rpgym.outbox.publish",
                TAG_EVENT_TYPE, eventType.name(), TAG_RESULT, "success").increment();

        meterRegistry.timer("rpgym.outbox.publish.duration",
                TAG_EVENT_TYPE, eventType.name()).record(publishDuration);

        // 음수 방어: 감사 시각과 발행 시각이 같은 밀리초일 때 0으로 떨어질 수 있다
        meterRegistry.timer("rpgym.outbox.lag",
                        TAG_EVENT_TYPE, eventType.name())
                .record(lag.isNegative() ? Duration.ZERO : lag);
    }

    public void recordPublishFailure(HealthEventType eventType) {
        meterRegistry.counter("rpgym.outbox.publish",
                TAG_EVENT_TYPE, eventType.name(), TAG_RESULT, "failure").increment();
    }

    public void recordDlq(HealthEventType eventType) {
        meterRegistry.counter("rpgym.outbox.dlq",
                TAG_EVENT_TYPE, eventType.name()).increment();
    }

    public void recordCleanupDeleted(int deletedCount) {
        meterRegistry.counter("rpgym.outbox.cleanup.deleted").increment(deletedCount);
    }

    public void updateBacklog(long pending, long failed) {
        pendingCount.set(pending);
        failedCount.set(failed);
    }
}