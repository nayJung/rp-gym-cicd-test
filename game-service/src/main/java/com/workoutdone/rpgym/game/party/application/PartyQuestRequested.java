package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.domain.PartyMetric;

import java.time.Instant;
import java.util.UUID;

/**
 * "이 파티에 파티 퀘스트를 만들어 달라" 는 Spring 이벤트. PartyEnded 와 같은 규약이다.
 *
 * 발행하는 party 가 타입을 소유하고, 구독하는 quest 가 import 한다. party 는 누가 듣는지 모른다.
 * 파티 생성 트랜잭션 안에서 발행되므로 구독자는 AFTER_COMMIT 으로 받아야 한다.
 *
 * 멤버 목록을 싣지 않는다. 생성 시점엔 파티장 한 명뿐이고, 파티 퀘스트는 일일 단위라
 * 퀘스트 쪽이 매일 PartyMemberRepository.findActiveUserIdsByPartyId() 로 그날 멤버를 다시 읽는 편이 맞다.
 *
 * @param partyId            파티
 * @param ownerId            생성자 = 파티장 = 요청자
 * @param metric             파티가 고른 지표. quest 쪽은 Metric.from(metric.name()) 으로 바꾼다
 * @param createdAt          파티 생성 시각
 * @param matchingDeadlineAt 모집 마감 예정 시각. 이 뒤로는 멤버가 안 늘어난다
 * @param endsAt             파티 수명 종료. 파티 퀘스트 기한의 상한
 */
public record PartyQuestRequested(
        UUID partyId,
        UUID ownerId,
        PartyMetric metric,
        Instant createdAt,
        Instant matchingDeadlineAt,
        Instant endsAt
) {
}
