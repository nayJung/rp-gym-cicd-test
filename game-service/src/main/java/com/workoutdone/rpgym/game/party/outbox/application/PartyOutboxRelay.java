package com.workoutdone.rpgym.game.party.outbox.application;

import com.workoutdone.rpgym.game.party.outbox.config.PartyOutboxProperties;
import com.workoutdone.rpgym.game.party.outbox.domain.PartyOutboxEvent;
import com.workoutdone.rpgym.game.party.outbox.domain.repo.PartyOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * party_outbox_events 에서 Kafka 로 보내는 발행기의 한 라운드. quest 의 OutboxRelay 와 같은 규칙이다.
 *
 * 한 건이라도 실패하면 라운드를 중단한다 — 뒤 이벤트를 먼저 보내면 같은 파티션 안에서 순서가 뒤집히고,
 * 브로커 장애면 남은 건도 전부 타임아웃이라 라운드가 sendTimeout x 건수만큼 늘어진다.
 * 상태 변경은 더티 체킹으로 반영되므로 save 가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyOutboxRelay {

    private final PartyOutboxEventRepository repository;
    private final PartyEventPublisherPort publisher;
    private final PartyOutboxProperties props;

    /** @return 이번 라운드에 발행 성공한 건수 */
    @Transactional
    public int relayOnce() {
        List<PartyOutboxEvent> pending = repository.findPendingForUpdate(props.pollSize());
        if (pending.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (PartyOutboxEvent event : pending) {
            if (!relay(event)) {
                log.warn("파티 outbox 발행 실패로 라운드를 중단한다. 성공={} 남은 대상={}",
                        published, pending.size() - published);
                break;
            }
            published++;
        }
        return published;
    }

    private boolean relay(PartyOutboxEvent event) {
        try {
            // payload 문자열을 그대로 보낸다. 재직렬화하면 event_id 가 바뀌어 소비 측 멱등이 깨진다.
            publisher.publish(props.topic(), event.getPartitionKey(), event.getPayload(), event.getEventType());
            event.markPublished(Instant.now());
            log.debug("파티 이벤트 발행 완료. eventType={} eventId={}", event.getEventType(), event.getEventId());
            return true;
        } catch (Exception e) {
            // Kafka 실패는 DB 트랜잭션을 오염시키지 않으므로 markRetried 는 정상 커밋된다.
            event.markRetried();
            logFailure(event, e);
            return false;
        }
    }

    private void logFailure(PartyOutboxEvent event, Exception e) {
        if (event.getRetryCount() >= props.escalateAfter()) {
            log.error("파티 이벤트 발행 실패가 {}회 누적됐다. 브로커 상태를 확인해야 한다. eventId={} eventType={}",
                    event.getRetryCount(), event.getEventId(), event.getEventType(), e);
            return;
        }
        log.warn("파티 이벤트 발행 실패. 다음 폴링에서 재시도한다. eventId={} 시도={} 원인={}",
                event.getEventId(), event.getRetryCount(), e.toString());
    }
}
