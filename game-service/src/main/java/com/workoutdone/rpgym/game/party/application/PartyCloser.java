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
/// PARTY_MATCHED가 두번 나가면 퀘스트담당이 파티퀘스트를 두 번 만든다.
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

        // ── 퀘스트 담당에게 ───────────────────────────────────────────────
        // 파티 퀘스트 생성 요청은 여기서 나간다. 이것 하나만 구독하면 된다.
        //
        //   토픽    game.events  (rpgym.party.outbox.topic — quest 와 같은 토픽)
        //   헤더    eventType = PARTY_MATCHED
        //   키      ownerId
        //   본문    { eventId, eventType, occurredAt, userId, data: PartyMatchedData }
        //
        // @KafkaListener 로 받는다. @TransactionalEventListener 가 아니다 —
        // 이 트랜잭션은 릴레이가 발행하기 한참 전에 이미 커밋돼 있다.
        // 컨슈머 그룹은 알림 쪽과 분리할 것. game-service 가 자기 토픽을 구독하게 되므로
        // 자기가 낸 QUEST_CREATED 등도 들어온다. eventType 으로 거르고 나머지는 넘겨야 한다.
        //
        // 파티당 정확히 1번이다. 마감 경로가 4개지만 위 closeRecruiting 조건부 UPDATE 가
        // 하나만 통과시키고, uk_party_outbox_events_aggregate 가 DB 레벨 2차 방어다.
        // 그래도 카프카는 at-least-once 라 재배달은 온다. 멱등키는 소비 측 책임이다.
        //
        // 수락 · 거절은 없다. 받으면 만드는 것이고, 만들지 말지 · 기한 · 보상 XP ·
        // 멤버별 baseline · 완료 판정 · XP 지급은 전부 퀘스트 판단이다. 파티는 관여하지 않는다.
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
