package com.workoutdone.rpgym.game.party.application.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 모집 마감 = 활동 시작. 4/4 도달 · 파티장 start · 마감 시각 경과 세 경로가 전부 이 이벤트 하나로 나간다.
 *
 * [#122 확정] 퀘스트 트리거가 아니다. 퀘스트는 이 이벤트를 구독하지 않는다 —
 * 파티 퀘스트는 파티장이 퀘스트 API 로 직접 만들고, 퀘스트가 그때 party_members 를 읽는다.
 * 파티 ↔ 퀘스트 는 Kafka · REST 없이 Spring 이벤트로만 주고받는다.
 * 받는 쪽은 알림뿐이다 (슬랙 "파티 활동 시작"). 아래 필드 설명은 그 관점에서 읽으면 된다.
 *
 * metric 은 문자열이다 — 프로세스 밖으로 나가는 계약이라 enum 을 그대로 실으면 소비 측이 우리 클래스를 알아야 한다.
 *
 *   partyId        파티 퀘스트가 걸릴 파티. parties.id (UUID 다 — BIGINT 아니다)
 *   metric         STEPS | ACTIVE_MINUTES | ACTIVE_CALORIES. 파티 생성 시 확정, 불변.
 *                  Metric.from(metric) 으로 바꿔 쓰면 된다
 *   memberUserIds  모집 마감 시점의 확정 명단. 여기 없는 사람은 이 파티 퀘스트에 못 낀다
 *   startedAt      모집이 닫힌 시각 = 활동 시작
 *   endsAt         파티 수명 종료(생성 + 7d). 퀘스트 기한의 '상한' 이다.
 *                  파티 퀘스트가 1일 단위라면 이 안에서 여러 번 만들어지게 된다
 *
 * 여기 실린 값은 전부 마감 이후로 바뀌지 않는다. 바뀌는 값(현재 인원 등)은 일부러 싣지 않았다 —
 * 소비 측이 낡은 값을 들고 있게 되기 때문이다. 그런 값이 필요하면 조회로 가져가야 한다.
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