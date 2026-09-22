package com.workoutdone.rpgym.game.outbox.adapter.out.kafka;

import com.workoutdone.rpgym.game.outbox.application.EventPublisherPort;
import com.workoutdone.rpgym.game.outbox.config.OutboxPublishProperties;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;

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
// T1 트랜잭션 끝난후 outbox에 저장된 데이터를 를 외부로 polling
@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher implements EventPublisherPort {

    /** 소비 측이 payload 역직렬화 전에 타입으로 필터링할 수 있게 하는 헤더. Health가 주는 것과 대칭이다. */
    private static final String HEADER_EVENT_TYPE = "eventType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublishProperties properties;

    @Override
    public void publish(String topic, String partitionKey, String payload, OutboxEventType eventType) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, payload);
        record.headers().add(HEADER_EVENT_TYPE, eventType.name().getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            log.debug("kafka 발행 완료. topic={} key={} eventType={}", topic, partitionKey, eventType);

        } catch (InterruptedException e) {
            // 인터럽트 상태를 삼키면 상위에서 종료 신호를 놓친다
            Thread.currentThread().interrupt();
            throw new IllegalStateException("kafka 발행이 중단됐다. topic=" + topic, e);

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("kafka 발행에 실패했다. topic=" + topic, e);
        }
    }
}
