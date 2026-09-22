package com.workoutdone.rpgym.game.xp.domain;

public enum SourceType {
    QUEST,
    // V6 이 ck_xp_ledgers_source_type 에 연 값이다. DDL 과 이 enum 은 같은 커밋에서 움직인다.
    ACHIEVEMENT
}
