package com.workoutdone.rpgym.game.party.domain;

/**
 * 파티가 고르는 지표. 파티 퀘스트는 이 하나만 본다.
 *
 * quest 의 Metric 과 값 이름이 같아야 한다 — 퀘스트 담당은 PartyQuestRequested 를 받아
 * Metric.from(partyMetric.name()) 으로 변환한다. 파티가 quest 패키지를 import 하지 않기 위해 따로 둔다.
 * 값을 추가하면 V5 의 ck_parties_metric 과 quest 의 Metric 도 같이 늘려야 한다.
 */
public enum PartyMetric {
    STEPS,
    ACTIVE_MINUTES,
    ACTIVE_CALORIES
}
