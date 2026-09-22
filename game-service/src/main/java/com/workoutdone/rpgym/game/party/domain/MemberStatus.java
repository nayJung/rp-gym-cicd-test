package com.workoutdone.rpgym.game.party.domain;

/** LEFT 는 soft delete. 행은 남고 랭킹 집계에 계속 잡힌다. */
public enum MemberStatus {
    ACTIVE,
    LEFT
}
