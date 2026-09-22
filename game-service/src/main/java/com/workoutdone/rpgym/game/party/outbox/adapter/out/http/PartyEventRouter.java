package com.workoutdone.rpgym.game.party.outbox.adapter.out.http;

import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.outbox.adapter.out.kafka.PartyOutboxKafkaPublisher;
import com.workoutdone.rpgym.game.party.outbox.application.PartyEventPublisherPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 파티 이벤트의 발행 수단을 여기서 나눈다. 릴레이는 하나다.
 *
 *   PARTY_MATCHED                → Kafka.  [#122 확정: 받는 쪽이 없다] 아래 참고.
 *   나머지 5종                    → HTTP.   받는 쪽이 알림이다. 슬랙 메시지를 만든다.
 *
 * 원래 PARTY_MATCHED 는 퀘스트가 구독해 파티 퀘스트를 만드는 트리거였다. 퀘스트 담당과 합의해 접었다 —
 * 파티 ↔ 퀘스트 는 같은 JVM 이라 Kafka · REST 를 쓰지 않고, 파티 퀘스트는 파티장이 퀘스트 API 로 직접 만들며,
 * 꼭 주고받을 게 있으면 Spring 이벤트다. 그래서 지금 Kafka 분기는 듣는 쪽이 없는 채로 나간다.
 * 알림이 "활동 시작" 슬랙을 원하면 PARTY_MATCHED 도 HTTP 로 돌리고 Kafka 분기 · PartyOutboxKafkaPublisher 를 지운다.
 * 코드는 그때 정리한다. 지금은 건드리지 않는다.
 *
 * 릴레이가 이 포트만 보고 있어서, 수단이 무엇인지는 릴레이도 아웃박스도 모른다.
 *
 * 실패는 반드시 예외로 알린다. 삼키면 릴레이가 발행에 성공한 줄 알고 PUBLISHED 로 바꿔
 * 그 이벤트가 영영 나가지 않는다. Feign 은 2xx 가 아니면 FeignException 을 던지므로 그대로 둔다.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class PartyEventRouter implements PartyEventPublisherPort {

    private final PartyOutboxKafkaPublisher kafkaPublisher;
    private final NotificationClient notificationClient;

    @Override
    public void publish(String topic, String partitionKey, String payload, PartyEventType eventType) {
        if (eventType == PartyEventType.PARTY_MATCHED) {
            kafkaPublisher.publish(topic, partitionKey, payload, eventType);
            return;
        }

        notificationClient.send(eventType.name(), payload);
        log.debug("알림 서비스 전달 완료. eventType={} key={}", eventType, partitionKey);
    }
}
