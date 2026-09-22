package com.workoutdone.rpgym.game.party.domain;

/** 파티 생명주기. RECRUITING 에서만 초대 · 매칭이 된다. */
public enum PartyStatus {
    RECRUITING,
    ACTIVE,
    ENDED,
    DISBANDED
}
