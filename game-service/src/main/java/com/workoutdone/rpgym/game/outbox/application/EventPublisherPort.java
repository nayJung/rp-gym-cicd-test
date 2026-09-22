package com.workoutdone.rpgym.game.outbox.application;

import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;

/**
 * 아웃바운드 포트. 구현은 Kafka.
 *
 * 인바운드 포트는 만들지 않지만 이건 만든다. Kafka는 라이브러리가 아니라 외부 시스템이고,
 * 구현이 어댑터에 있으며 테스트에서 갈아끼워야 한다. ObjectMapper에 포트를 두지 않은 것과 같은 잣대다.
 */
public interface EventPublisherPort {

    /**
     * 발행에 성공하면 정상 반환하고, 실패하면 예외를 던진다.
     *
     * "보냈다"를 브로커 응답으로 확인해야 Outbox를 PUBLISHED로 전이할 수 있다.
     * fire-and-forget으로 두면 브로커가 받지 못한 이벤트를 PUBLISHED로 찍는다.
     */
    void publish(String topic, String partitionKey, String payload, OutboxEventType eventType);
}
