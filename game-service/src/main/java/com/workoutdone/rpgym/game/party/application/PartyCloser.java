package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.outbox.application.OutboxRecorder;
import com.workoutdone.rpgym.game.outbox.domain.AggregateType;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;
import com.workoutdone.rpgym.game.party.application.payload.PartyMatchedData;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
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
    private final PartyInvitationRepository invitationRepository;
    private final OutboxRecorder outboxRecorder;

    public record Closed(Party party,
                         List<UUID> memberUserIds,
                         int canceledInvitations) {}


    ////로그의 by= 값. 어느 경로가 마감시켰는지 운영에서 구분하기 위한 것
    public enum Trigger { FULL, DEADLINE, OWNER }


    //MANDATORY: 호출자의 트랜잭션 안에서만 실행된다. 자리확보와 마감이 다른 트랜잭션에 있으면 안됨.
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

        int canceled = invitationRepository.cancelAllPending(partyId);
        List<UUID> members = memberRepository.findActiveUserIdsByPartyId(partyId);

        outboxRecorder.append(
                AggregateType.PARTY,
                partyId,
                OutboxEventType.PARTY_MATCHED,
                party.getOwnerId(),
                now,
                new PartyMatchedData(partyId, party.getPartyName(),
                        party.getOwnerId(), members, now, party.getEndsAt())
        );

        log.info("파티 모집 마감. partyId={} by={} members={}/{} canceledInvitations={} endsAt={}",
                partyId, trigger, party.getCurrentMember(), party.getMaxMember(), canceled, party.getEndsAt());
        return new Closed(party, members, canceled);
    }












}
