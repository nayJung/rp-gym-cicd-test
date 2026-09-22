package com.workoutdone.rpgym.game.quest.domain;

public enum SuggestionStatus {
    // 아직 유저가 아무것도 고르지 않았다. 30분이 지나면 이 상태인 채로 만료된다.
    // 만료를 별도 상태로 두지 않는 이유는 만료가 expires_at 으로 계산되는 값이기 때문이다.
    // 상태 컬럼에 복사해두면 그 복사를 누가 언제 하느냐는 문제가 생기고, 복사는 항상 늦는다.
    PENDING,
    // 유저가 수락해서 Quest 가 만들어졌다. quest_id 가 채워진다.
    ACCEPTED,
    // 유저가 거절했다. Quest 는 만들어지지 않는다.
    REJECTED,
    // 이 제안이 근거한 스냅샷이 더 이상 최신이 아니다.
    // 제안과 수락 사이에 건강 데이터 동기화가 한 번 끼어들었다는 뜻이고,
    // 그대로 수락시키면 그 사이에 쌓인 활동이 소급 인정되어 공짜로 완료된다.
    SUPERSEDED
}
