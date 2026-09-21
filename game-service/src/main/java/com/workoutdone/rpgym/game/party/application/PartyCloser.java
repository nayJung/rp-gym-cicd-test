package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.application.payload.PartyMatchedData;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// 모집 마감의 유일한 경로
//// RECRUITING -> ACTIVE 전이와 그에 딸린 후처리(PENDING 초대 취소, PARTY_MATCHED 발행).
////마감 경로는 4개 : 최대인원 4명도달하거나 초대 수락하거나 매칭하거나 파티장이 시작하거나 배치(마감 시각 24시 경과)가 다되거나
/// 넷이 같은 순간에 와도 조건부 UPDATE 는 하나만 성공되고 성공한 호출만 후처리를 시작.
/// PARTY_MATCHED가 두번 나가면 알림이 "활동 시작" 슬랙을 두 번 보낸다. (퀘스트는 이 이벤트를 안 듣는다 — #122)
/// 그래서 이 클래스 밖에서는
///closeRecruiting을 직접부르지않음
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyCloser {



    private final PartyRepository partyRepository;
    private final PartyMemberRepository memberRepository;
    private final PartyInvitationCloser invitationCloser;
    private final PartyOutboxRecorder outboxRecorder;

    public record Closed(Party party,
                         List<UUID> memberUserIds,
                         int canceledInvitations) {}


    ////로그의 by= 값. 어느 경로가 마감시켰는지 운영에서 구분하기 위한 것
    public enum Trigger { FULL, DEADLINE, OWNER }


    //MANDATORY: 호출자의 트랜잭션 안에서만 실행된다. 자리확보와 마감이 다른 트랜잭션에 있으면 안됨.
    //MANDATORY 인 이유 — "자리 확보" 와 "마감" 이 서로 다른 트랜잭션에 있으면, 자리 확보는 커밋됐는데 마감은 실패하는 상태가 생김
    //호출자의 트랜잭션 안에서만 돌게 강제해서 둘이 항상 같이 커밋되거나 같이 롤백되게함
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Closed> close(UUID partyId, Instant now, Trigger trigger){
        if (!partyRepository.closeRecruiting(partyId, now)){
            log.debug("모집 마감 건너뜁 = 이미 RECRUITING이 아님. partyId={} by={}", partyId, trigger);
            return Optional.empty();
        }
        return Optional.of(afterClose(partyId, now, trigger));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Closed> closeByOwner(UUID partyId, UUID ownerId, Instant now){

        if(!partyRepository.closeRecruitingByOwner(partyId, ownerId, now)){
            return Optional.empty();
        }
        return Optional.of(afterClose(partyId, now, Trigger.OWNER));
    }




    private Closed afterClose(UUID partyId, Instant now, Trigger trigger){
        // closeRecruiting이 clearAutomatically라서 여기서읽는 Party는 ACTIVE.

        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("방금 닫은 파티가 없습니다: " + partyId));

        // 남은 초대를 거두고 건마다 PARTY_INVITATION_CLOSED 를 싣는다 — 슬랙 버튼을 거둘 수단이 이것뿐이다.
        int canceled = invitationCloser.closeAllPending(party, InvitationCloseReason.PARTY_CLOSED, now);
        List<UUID> members = memberRepository.findActiveUserIdsByPartyId(partyId);

        // ── PARTY_MATCHED 는 퀘스트 트리거가 아니다 (#122 확정) ─────────────────
        // 퀘스트 담당과 합의: 파티 → 퀘스트 통신은 Kafka 도 REST 도 쓰지 않는다. 같은 JVM 이다.
        //
        //   · 파티 퀘스트 생성은 퀘스트가 "파티장이 호출하는 API" 를 직접 만든다.
        //     퀘스트가 metric 을 검증하고, party_members 를 그때 읽어 팀 퀘스트를 만든다.
        //     파티가 언제 생성되고 언제 마감됐는지 퀘스트는 알 필요가 없다.
        //   · 그래서 이 이벤트를 퀘스트가 구독하지 않는다. 퀘스트는 파티 outbox 를 보지 않는다.
        //   · 파티 ↔ 퀘스트 사이에 꼭 주고받을 게 생기면 Spring 이벤트다 (PartyEnded 규약과 동일,
        //     발행 측이 타입 소유 · 구독 측이 import · AFTER_COMMIT).
        //   · 슬랙에서 팀 퀘스트 수락 · 거절은 없다. 파티장이 API 로 만드는 순간 확정이다.
        //
        // 아래 append 는 그대로 둔다. 모집 마감 = 활동 시작 알림(슬랙)으로는 쓸 수 있어서다.
        // 파티당 정확히 1번인 건 변함없다 — closeRecruiting 조건부 UPDATE + uk_party_outbox_events_aggregate.
        // 상세: https://github.com/workout-done/rp-gym/issues/122
        // ──────────────────────────────────────────────────────────────────
        outboxRecorder.append(
                PartyAggregateType.PARTY,
                partyId,
                PartyEventType.PARTY_MATCHED,
                party.getOwnerId(),
                now,
                new PartyMatchedData(partyId, party.getPartyName(),
                        party.getOwnerId(), party.getMetric().name(), members, now, party.getEndsAt())
        );

        log.info("파티 모집 마감. partyId={} by={} metric={} members={}/{} canceledInvitations={} endsAt={}",
                partyId, trigger, party.getMetric(), party.getCurrentMember(), party.getMaxMember(),
                canceled, party.getEndsAt());
        return new Closed(party, members, canceled);
    }












}
