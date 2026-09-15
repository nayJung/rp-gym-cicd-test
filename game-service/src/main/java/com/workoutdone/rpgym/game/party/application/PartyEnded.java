package com.workoutdone.rpgym.game.party.application;

import java.time.Instant;
import java.util.UUID;

//// 파티가 끝났다는 사실만 알리는 Spring 이벤트로 xp의 XpGranted와 같은 규약이다.
/// 이 record가 party/application에 있는것이 의존방향을 정함.
/// 발행하는 쪽(party)이 타입을 소유하고, 구독하는 쪽(ranking)이 import 한다.
/// party는 누가 듣는지 모른다.
/// weeklyXp를 싣지 않고 구독자가 xp_leagers를 절대값으로 다시 합산해 덮어쓰면 되고, 그래야 멱등하다.
/// partyId는 종료된파티
/// parties.ends_at은 구독자는 이 시각이 속한 주를 다시 계산함.
public record PartyEnded(
        UUID partyId,
        Instant endedAt
) {
}
