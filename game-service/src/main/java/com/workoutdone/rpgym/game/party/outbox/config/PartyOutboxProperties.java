package com.workoutdone.rpgym.game.party.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 파티 outbox 릴레이 설정. quest 의 rpgym.outbox 와 키 구조가 같다.
 *
 * topic 기본값이 game.events 인 이유 — 소비 측(Notification)이 토픽 하나를 eventType 헤더로 분기한다.
 * 파티 이벤트만 다른 토픽으로 보내면 소비 측이 두 토픽을 구독해야 한다. 값을 바꾸려면 Notification 과 맞출 것.
 */
@ConfigurationProperties(prefix = "rpgym.party.outbox")
public record PartyOutboxProperties(
        @DefaultValue("game.events") String topic,
        // 한 라운드에 집을 PENDING 최대 건수. 트랜잭션 동안 잠기므로 무작정 키우지 않는다.
        @DefaultValue("100") int pollSize,
        // Kafka 발행 응답 대기. 여기서 포기한 뒤 프로듀서가 늦게 성공하면 같은 이벤트가 두 번 나가지만
        // 소비 측이 event_id 로 멱등하므로 안전하다(at-least-once).
        @DefaultValue("5s") Duration sendTimeout,
        // 재시도가 이 횟수를 넘으면 로그를 WARN 에서 ERROR 로 올린다. 상태는 여전히 PENDING 이다.
        @DefaultValue("10") int escalateAfter
) {
}
