package com.workoutdone.rpgym.game.party.domain;

/**
 * 파티가 game.events 로 내보내는 이벤트 종류. party_outbox_events.event_type 과 Kafka 헤더 eventType 에 그대로 실린다.
 *
 * quest 의 OutboxEventType 과 별개다. 파티 이벤트를 추가할 때 quest 쪽 enum · CHECK 제약을 건드리지 않기 위해
 * 파티가 자기 outbox 를 따로 가진다. 값을 추가하면 V5 의 ck_party_outbox_events_event_type 도 같이 늘려야 한다.
 */
public enum PartyEventType {
    PARTY_INVITED,
    /**
     * 유저가 응답하지 않은 채로 초대가 닫혔다 (만료 · 파티 마감 · 해산).
     * 수락 · 거절은 유저가 슬랙 버튼을 눌러 REST 응답을 이미 받으므로 이벤트가 없다.
     * 응답 없이 닫힌 초대는 알릴 방법이 이것뿐이라, 알림이 이 이벤트로 슬랙 버튼을 거둔다.
     */
    PARTY_INVITATION_CLOSED,
    PARTY_MEMBER_JOINED,
    PARTY_MEMBER_LEFT,
    PARTY_MATCHED,
    PARTY_ENDED
}
