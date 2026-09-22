package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.application.payload.PartyInvitationClosedData;
import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 초대가 끝났다는 사실을 알림에 알리는 유일한 경로.
 *
 * 슬랙 버튼의 응답이 REST 가 아니라 이벤트로 오가므로, 초대의 끝을 유저에게 보여줄 수단이
 * PARTY_INVITATION_CLOSED 하나뿐이다. 이벤트가 빠지면 그 사람 슬랙에는 누를 수 있는 버튼이
 * 영원히 남는다. 그래서 상태 전이와 이벤트 적재를 이 클래스 안에 묶어 둔다.
 *
 * 초대 하나당 CLOSED 는 정확히 한 번이다 (uk_party_outbox_events_aggregate 가 그걸 강제한다).
 * 따라서 전이가 실제로 일어난 호출만 이벤트를 실어야 한다 — 이미 닫힌 초대에 또 실으면
 * 유니크 위반으로 트랜잭션이 통째로 깨진다.
 *
 * MANDATORY — 전이와 적재가 호출자의 트랜잭션에서 함께 커밋되거나 함께 롤백돼야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyInvitationCloser {

    private final PartyInvitationRepository invitationRepository;
    private final PartyOutboxRecorder outboxRecorder;

    /** 파티에 남은 PENDING 초대를 전부 닫는다 (마감 · 해산). @return 실제로 닫힌 건수 */
    @Transactional(propagation = Propagation.MANDATORY)
    public int closeAllPending(Party party, InvitationCloseReason reason, Instant now) {
        int closed = 0;
        for (PartyInvitation invitation : invitationRepository.findPendingByPartyId(party.getId())) {
            if (close(invitation, party.getPartyName(), reason, now)) {
                closed++;
            }
        }
        return closed;
    }

    /**
     * 유저 응답 없이 초대 한 건을 닫는다 (만료 · 마감 · 해산). 전이까지 여기서 한다.
     * @return true = 이 호출이 닫았다. false = 그 사이 유저가 응답했다 (그쪽이 CLOSED 를 싣는다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean close(PartyInvitation invitation, String partyName,
                         InvitationCloseReason reason, Instant now) {
        boolean transitioned = reason == InvitationCloseReason.EXPIRED
                ? invitationRepository.markExpired(invitation.getId())
                : invitationRepository.markCanceled(invitation.getId());

        if (!transitioned) {
            log.debug("초대 닫기 건너뜀 — 이미 PENDING 이 아님. invitationId={} reason={}",
                    invitation.getId(), reason);
            return false;
        }
        record(invitation, partyName, reason, now);
        return true;
    }

    /**
     * 전이를 이미 끝낸 경로가 이벤트만 싣는다 (수락 · 거절 · 정원 초과).
     *
     * 수락은 markAccepted 와 자리 확보가 한 덩어리라 전이를 여기로 옮길 수 없다.
     * 호출자는 반드시 "조건부 UPDATE 가 1 을 돌려준" 경로에서만 불러야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordClosed(PartyInvitation invitation, String partyName,
                             InvitationCloseReason reason, Instant now) {
        record(invitation, partyName, reason, now);
    }

    private void record(PartyInvitation invitation, String partyName,
                        InvitationCloseReason reason, Instant now) {
        UUID invitationId = invitation.getId();

        outboxRecorder.append(
                PartyAggregateType.PARTY_INVITATION,
                invitationId,
                PartyEventType.PARTY_INVITATION_CLOSED,
                invitation.getInviteeId(),   // PARTY_INVITED 와 같은 대상이어야 슬랙 메시지를 찾아간다
                now,
                PartyInvitationClosedData.of(invitationId, invitation.getPartyId(), partyName,
                        invitation.getInviteeId(), reason, now));

        log.info("초대 종료. invitationId={} partyId={} inviteeId={} reason={} status={}",
                invitationId, invitation.getPartyId(), invitation.getInviteeId(), reason, reason.status());
    }
}
