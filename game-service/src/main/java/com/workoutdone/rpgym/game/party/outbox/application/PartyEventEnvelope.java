package com.workoutdone.rpgym.game.party.outbox.application;

import java.time.Instant;
import java.util.UUID;

/**
 * game.events 에 실리는 공통 envelope. quest 의 EventEnvelope 와 필드가 같아야 한다 —
 * 소비 측(Notification)은 토픽 하나를 같은 모양으로 읽는다. userId 가 곧 알림 대상이다.
 */
public record PartyEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID userId,
        Object data
) {
}
