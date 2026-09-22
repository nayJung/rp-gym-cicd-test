package com.workoutdone.rpgym.game.party.outbox.application;

import com.workoutdone.rpgym.game.party.domain.PartyEventType;

/** 릴레이가 브로커를 모르게 하는 포트. 구현은 adapter/out/kafka. */
public interface PartyEventPublisherPort {

    /** 발행 실패는 예외로 알린다 — 릴레이가 잡아서 PENDING 으로 남긴다. */
    void publish(String topic, String partitionKey, String payload, PartyEventType eventType);
}
