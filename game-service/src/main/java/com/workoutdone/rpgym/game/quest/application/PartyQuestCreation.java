package com.workoutdone.rpgym.game.quest.application;

// 파티 퀘스트 생성 결과다.
// 생성은 수락과 달리 실패해도 남겨야 할 상태 변경이 없다. 그래서 예외로 처리해도 틀리지 않는다.
// 그래도 결과 타입으로 돌려주는 쪽을 택한 이유는 실패 사유가 여덟 가지라
// 예외를 그만큼 만들거나 하나에 사유를 담아야 하기 때문이다.
public sealed interface PartyQuestCreation {

    record Created(PartyQuestView view) implements PartyQuestCreation {}

    record Failed(Reason reason) implements PartyQuestCreation {}

    enum Reason {
        // 요청자가 그 파티의 활성 멤버가 아니다. 소속된 파티가 아예 없는 경우도 여기로 온다.
        NOT_A_MEMBER,

        // 파티 멤버이긴 하지만 파티장이 아니다.
        // 무엇을 얼마나 할지는 파티장이 정한다고 파티 담당자와 합의했다.
        NOT_OWNER,

        // 파티가 모집 중이거나 이미 끝났다.
        // 모집 중에 만들면 나중에 들어온 멤버가 명단에 없는 채로 남는다.
        // 파티 퀘스트는 시작 후 중도 합류가 안 되기 때문에 그 멤버는 끝까지 기여할 수 없다.
        PARTY_NOT_ACTIVE,

        // 파티에 활성 멤버가 없거나 정원을 넘는다. 파티 쪽 불변식이 깨진 경우다.
        INVALID_MEMBERS,

        // 목표가 0 이하다. 첫 스냅샷에서 바로 완료되어 XP 가 공짜로 나간다.
        INVALID_TARGET,

        // 제목이 비었거나 100 자를 넘는다.
        // 카프카로 들어오는 제안과 달리 여기서는 잘라내지 않고 거절한다.
        // 사람이 직접 입력한 값이고 바로 고칠 수 있기 때문이다.
        INVALID_TITLE,

        // 이 파티에 이미 살아 있는 파티 퀘스트가 있다.
        PARTY_QUEST_ALREADY_ACTIVE,

        // 오늘이 거의 끝나서 만들 수 있는 기한이 남지 않았다.
        // 파티 퀘스트는 당일 자정 직전에 끝난다. 건강 데이터가 자정마다 0 으로 돌아가기 때문에
        // 날짜를 넘겨서 만들 수가 없다.
        TOO_LATE_IN_DAY
    }
}
