package com.workoutdone.rpgym.game.party.adapter.in.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Notification 이 notification.events 토픽에 싣는 공통 envelope.
 *
 * HealthEventEnvelope 와 같은 모양이다 — eventType 을 String 으로 받는 이유도 같다.
 * enum 으로 받으면 모르는 값이 왔을 때 역직렬화가 터지고, 컨슈머의 예외는 offset 미커밋,
 * 즉 그 파티션의 무한 재시도가 된다. 모르는 타입은 "처리했고 결론이 무시" 여야 한다.
 *
 * userId 는 슬랙 버튼을 누른 사람이다. Notification 이 슬랙 user.id 를 플랫폼 UUID 로
 * 바꿔서 싣는다. 이 값이 틀리면 초대 대상이 아니라는 이유로 거부된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationEventEnvelope(
        UUID eventId,
        String eventType,
        UUID userId,
        JsonNode data
) {
}
