package com.workoutdone.rpgym.game.quest.domain.repo;

import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PartyQuestRepository {

    Optional<PartyQuest> findById(UUID partyQuestId);

    PartyQuest save(PartyQuest partyQuest);

    // 이 유저가 속한 살아 있는 파티 퀘스트를 찾는다.
    // 만료를 여기서 거른다. 만료된 퀘스트의 상태를 배치로 바꾸지 않기 때문이다.
    // 만료는 저장된 시각으로 계산되는 값이고, 계산할 수 있는 것을 저장하면
    // 그 저장을 누가 언제 갱신하느냐는 문제가 생긴다. 그리고 갱신은 항상 늦는다.
    // 기준 시각으로 서버 시계가 아니라 이벤트가 들고 온 측정 시각을 받는다.
    // 컨슈머가 죽었다가 자정을 넘겨 살아나도 만료 전에 목표를 채운 파티는 보상을 받아야 한다.
    // 여기서 전부 걸러내기 때문에 만료 후 도착한 기여는 멤버 행에도 카운터에도 반영되지 않는다.
    // 한쪽만 반영되는 경우가 없으므로 둘이 같아야 한다는 등식은 그대로 성립한다.
    Optional<PartyQuest> findActiveByUserId(UUID userId, Instant at);

    // 이 파티에 아직 살아 있는 파티 퀘스트가 있는지 본다.
    // 파티당 진행 중인 퀘스트는 하나다. 여러 개를 허용하면 같은 건강 데이터가
    // 여러 퀘스트에 동시에 기여하게 되고, 그 자체가 틀린 것은 아니지만
    // 유저가 자기 걸음이 어디에 얼마나 들어갔는지 알 수 없게 된다.
    // 이 판정을 DB 의 부분 유니크 인덱스로 강제하지 않았다.
    // 그러면 만료됐지만 아직 상태가 안 바뀐 행이 새 퀘스트 생성을 막는다.
    // 만료를 계산으로 판정하기로 했으므로 이 조회도 시각으로 거른다.
    boolean existsActiveByPartyId(UUID partyId, Instant at);

    // 공유 카운터를 증분으로 올린다.
    // 읽어서 애플리케이션에서 더한 값을 쓰면, 읽은 뒤 쓰기 전에 들어온
    // 다른 멤버의 갱신을 덮어쓴다. 네 명이 동시에 올리면 셋의 기여가 사라진다.
    int addToCurrentValue(UUID partyQuestId, int delta);

    // 중ㅇ요한 db 포트 계약서. 완료를 선점한다. 한 트랜잭션만 1 을 돌려받는다.
    // 이 한 문장이 XP 중복 지급을 막는 지점이다.
    // 상태를 읽어서 애플리케이션에서 판단하고 조건 없이 UPDATE 하면 네 명이 전부 통과해서
    // 네 배의 XP 가 나간다.
    // 위의 카운터 갱신이 이 행에 잠금을 걸기 때문에 동시에 들어온 트랜잭션들이 여기서 줄을 서고,
    // 잠금이 풀린 뒤 갱신된 값으로 조건을 다시 따진다. 그래서 하나만 통과한다.
    // 돌려받은 값이 1 일 때만 XP 를 지급해야 한다. 확인하지 않고 지급하면
    // 원장의 유니크 제약이 막아주기는 하지만 예외가 사람 수만큼 터진다.
    int claimCompletion(UUID partyQuestId, Instant at);
}
