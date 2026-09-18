package com.workoutdone.rpgym.game.ranking.domain;


import com.workoutdone.rpgym.game.party.domain.PartyWeek;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/////주간 파티 랭킹 저장소. 구현은 Redis Sorted Set, 키는 주차별. party:ranking:2026-W38
/// 개인 랭킹과 같은 모양으로 만듬. 주차 키가 하나 더 붙을 뿐.
public interface PartyRankingStore {

    record ScoredParty(UUID partyId, long weeklyXp){}

    //절대값 덮어쓰기(ZADD). 같은 값으로 반복 호출해도 결과가 같다.
    void save(PartyWeek week, UUID partyId, long weeklyXp);

    Optional<Long> findScore(PartyWeek week, UUID partyId);

    ////준 경쟁 순위용. score 가 정수라 하한을 score+1 로 주면 동점자를 뺀 "나보다 높은 수" 가 나온다.
    long countHigherThan(PartyWeek week, long weeklyXp);

    long size(PartyWeek week);

    ////점수 내림차순 [start, end] (0-based, 끝 포함)
    List<ScoredParty> findPage(PartyWeek week, long start, long end);
}
