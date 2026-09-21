package com.workoutdone.rpgym.game.party.outbox.adapter.out.http;

import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.outbox.adapter.out.kafka.PartyOutboxKafkaPublisher;
import com.workoutdone.rpgym.game.party.outbox.application.PartyEventPublisherPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 파티 이벤트를 받는 쪽이 둘로 갈렸다(#122). 릴레이는 하나이므로 여기서 나눈다.
 *
 *   PARTY_MATCHED                → Kafka.  받는 쪽이 퀘스트다. 파티 퀘스트 생성 요청이다.
 *   나머지 5종                    → HTTP.   받는 쪽이 알림이다. 슬랙 메시지를 만든다.
 *
 * 알림 쪽이 카프카를 쓰지 않기로 해서 알림용만 HTTP 로 내보낸다. 퀘스트는 같은 프로세스 안에 있지만
 * 자기 자신에게 HTTP 를 칠 이유가 없고, 아웃박스가 보장하는 유실 방지를 그대로 쓰려고 카프카로 남긴다.
 *
 * 릴레이가 이 포트만 보고 있어서, 수단이 무엇인지는 릴레이도 아웃박스도 모른다.
 * 나중에 알림이 카프카로 돌아오면 이 클래스만 지우면 된다.
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
