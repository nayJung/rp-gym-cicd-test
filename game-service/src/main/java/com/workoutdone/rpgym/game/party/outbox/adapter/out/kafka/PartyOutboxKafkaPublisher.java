package com.workoutdone.rpgym.game.party.outbox.adapter.out.kafka;

import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.outbox.application.PartyEventPublisherPort;
import com.workoutdone.rpgym.game.party.outbox.config.PartyOutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartyOutboxKafkaPublisher implements PartyEventPublisherPort {

    /** 소비 측이 payload 를 열기 전에 타입으로 거를 수 있게 하는 헤더. quest 발행기와 같은 이름이어야 한다. */
    private static final String HEADER_EVENT_TYPE = "eventType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PartyOutboxProperties props;

    @Override
    public void publish(String topic, String partitionKey, String payload, PartyEventType eventType) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, payload);
        record.headers().add(HEADER_EVENT_TYPE, eventType.name().getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(props.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            log.debug("kafka 발행 완료. topic={} key={} eventType={}", topic, partitionKey, eventType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 인터럽트를 삼키면 종료 신호를 놓친다
            throw new IllegalStateException("kafka 발행이 중단됐다. topic=" + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("kafka 발행에 실패했다. topic=" + topic, e);
        }
    }
}
