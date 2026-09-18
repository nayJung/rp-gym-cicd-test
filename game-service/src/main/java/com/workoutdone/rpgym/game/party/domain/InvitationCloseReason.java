package com.workoutdone.rpgym.game.party.domain;

/**
 * 초대가 끝난 이유. PARTY_INVITATION_CLOSED 페이로드에 실려, 알림이 슬랙 메시지의 버튼을 거두고
 * 대신 띄울 문구를 고르는 데 쓴다.
 *
 * 저장되는 상태와 1:1 이 아니다 — 파티 마감 · 해산 · 정원 초과는 모두 CANCELED 로 남지만
 * 유저에게 할 말("자리가 찼어요" / "파티가 사라졌어요")이 다르다.
 *
 * 수락 · 거절까지 여기 있는 이유 — 슬랙 버튼의 결과를 이벤트로 돌려주는 구조라
 * 유저가 누른 결과든 시간이 닫은 결과든 알림 입장에서는 "이 초대는 끝났다" 하나로 읽혀야 한다.
 */
public enum InvitationCloseReason {

    /** 유저가 수락했다. */
    ACCEPTED(InvitationStatus.ACCEPTED),

    /** 유저가 거절했다. */
    REJECTED(InvitationStatus.REJECTED),

    /** 수락을 눌렀지만 그 사이 자리가 찼다. 유저 입장에서는 실패다. */
    PARTY_FULL(InvitationStatus.CANCELED),

    /** 초대 TTL 경과. 만료 배치가 닫는다. */
    EXPIRED(InvitationStatus.EXPIRED),

    /** 파티가 모집을 마감했다(정원 · 마감 시각 · 파티장 시작). 남은 초대는 갈 자리가 없다. */
    PARTY_CLOSED(InvitationStatus.CANCELED),

    /** 마지막 멤버가 나가 파티가 사라졌다. */
    PARTY_DISBANDED(InvitationStatus.CANCELED);

    private final InvitationStatus status;

    InvitationCloseReason(InvitationStatus status) {
        this.status = status;
    }

    public InvitationStatus status() {
        return status;
    }
}
