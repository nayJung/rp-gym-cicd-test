package com.workoutdone.rpgym.game.achievement.domain;

/**
 * 업적 달성 조건. V6 ck_achievements_condition_type 과 같은 값이어야 한다.
 *
 * 둘 다 입력은 DAILY_GOAL_COMPLETED 하나뿐이다. 다른 점은 "어제를 안 했을 때" 뿐이다 --
 * COUNT 는 그대로 이어 세고, STREAK 는 1로 되돌아간다.
 */
public enum ConditionType {
    /** 일일 목표 누적 달성 횟수. 최초 달성 = 1 */
    DAILY_GOAL_COUNT,
    /** 일일 목표 연속 달성 일수. 하루 빠지면 1부터 */
    DAILY_GOAL_STREAK
}
