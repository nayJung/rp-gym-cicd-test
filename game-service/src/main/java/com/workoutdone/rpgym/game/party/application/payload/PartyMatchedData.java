package com.workoutdone.rpgym.game.party.application.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 모집 마감 = 활동 시작. 퀘스트 담당의 파티 퀘스트 시작 트리거.
 * 4/4 도달 · 파티장 start · 마감 시각 경과 세 경로가 전부 이 이벤트 하나로 나간다.
 *
 * metric 은 문자열이다 — Kafka 밖으로 나가는 계약이라 enum 을 그대로 실으면 소비 측이 우리 클래스를 알아야 한다.
 */
public record PartyMatchedData(
        UUID partyId,
        String partyName,
        UUID ownerId,
        String metric,
        List<UUID> memberUserIds,
        Instant startedAt,
        Instant endsAt
) {

}