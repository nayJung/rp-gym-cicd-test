package com.workoutdone.rpgym.game.party.outbox.domain;

/** FAILED 가 없다. 발행 실패는 PENDING 으로 남겨 다음 폴링이 다시 집는다 — 되살릴 경로가 없는 상태를 만들지 않는다. */
public enum PartyOutboxStatus {
    PENDING,
    PUBLISHED
}
