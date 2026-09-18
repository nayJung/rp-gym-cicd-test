package com.workoutdone.rpgym.game.party.application;


import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.application.payload.PartyMemberLeftData;
import com.workoutdone.rpgym.game.party.application.view.LeaveResultView;
import com.workoutdone.rpgym.game.party.application.view.PartyView;
import com.workoutdone.rpgym.game.party.application.view.StartResultView;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


//// 생성, 조기마감, 탈퇴 로직

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyCommandService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository memberRepository;
    private final PartyInvitationRepository invitationRepository;
    private final MemberEnroller enroller;
    private final PartyCloser closer;
    private final PartyOutboxRecorder outboxRecorder;
    private final ApplicationEventPublisher events;
    private final PartyProperties props;
    private final Clock clock;


    ///파티 생성. parties + party_members(OWNER) 가 한 트랜잭션임.
    ///metric 은 필수 — 파티 퀘스트가 이 지표 하나만 본다.
    ///생성한 사람이 파티장이고, 파티장이 만든다는 것 자체가 퀘스트 생성 요청이다. 별도 API 없이 여기서 PartyQuestRequested 를 발행한다.
    @Transactional
    public PartyView create(UUID userId, String partyName, PartyVisibility visibility, PartyMetric metric){
        enroller.assertNotInParty(userId);

        Instant now = clock.instant();
        Party party = partyRepository.save(Party.create(
                UUID.randomUUID(), partyName, userId,
                visibility == null ? PartyVisibility.PRIVATE : visibility,
                metric,
                props.maxMember(), now, props.recruitDuration(),
                props.lifetime()
        ));

        PartyMember owner = enroller.enroll(PartyMember.owner(UUID.randomUUID(), party.getId(), userId, now));

        // 퀘스트 생성 요청. 커밋 뒤 quest 가 받는다. 퀘스트 생성이 실패해도 파티 생성은 남는다.
        events.publishEvent(new PartyQuestRequested(
                party.getId(), userId, party.getMetric(),
                now, party.getMatchingDeadlineAt(), party.getEndsAt()));

        log.info("파티 생성. party={} ownerId={} visibility={} metric={} members={}/{} matchingDeadlineAt={}",
                party.getId(), userId, party.getVisibility(), party.getMetric(),
                party.getCurrentMember(), party.getMaxMember(), party.getMatchingDeadlineAt());

        return PartyView.of(party, List.of(owner), now);


     }



    ///파티장 조기 마감. "이 인원으로 시작"
    @Transactional
    public StartResultView start(UUID userId, UUID partyId){
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.PARTY_NOT_FOUND));

        Instant now = clock.instant();
        PartyCloser.Closed closed = closer.closeByOwner(partyId, userId, now).orElseThrow(() -> {
            // 조건부 UPDATE 가 0 이면 둘 중 하나다. 어느 쪽인지 갈라서 403 / 409 를 준다.
            if (!party.isOwner(userId)) {
                log.debug("조기 마감 거부 — 파티장 아님. partyId={} userId={} ownerId={}", partyId, userId, party.getOwnerId());
                return new PartyException(PartyErrorCode.NOT_PARTY_OWNER);
            }
            log.debug("조기 마감 거부 — 모집 중 아님. partyId={} status={}", partyId, party.getStatus());
            return new PartyException(PartyErrorCode.PARTY_NOT_RECRUITING);
        });

        // "파티 모집 마감. by=OWNER" 는 PartyCloser 가 남긴다. 여기서 또 쓰면 두 줄이 된다.

        return new StartResultView(
                partyId,
                closed.party().getCurrentMember(),
                closed.party().getMaxMember(),
                closed.canceledInvitations(),
                now,
                closed.party().getEndsAt());
    }



    ///탈퇴. 파티 행을 잠그고 멤버 LEFT/카운터 −1, 승계, 해산을 한 번에 한다.
    ///탈퇴는 드물어 락 경합이 없고, 조건부 업데이트 한 문장으로는 4개를 못한다.
    @Transactional
    public LeaveResultView leave(UUID userId){
        PartyMember me = memberRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.NOT_IN_PARTY));

        Party party = partyRepository.findByIdForUpdate(me.getPartyId())
                .orElseThrow(() -> new IllegalArgumentException("멤버는 있는데 파티가 없습니다: " + me.getPartyId()));

        Instant now = clock.instant();
        me.leave(now);
        memberRepository.save(me);// saveAndFlush, 아래 findActiveByPartyId 에서 나를 뺴고 읽기 위해
        party.memberLeft(); // 0이 되면 DISBANDED

        UUID newOwnerId = null;
        if (me.isOwner() && party.getStatus() != PartyStatus.DISBANDED){
            //// joined_at 오름차순 첫 사람. 탈퇴한 나는 이미 LEFT 라 목록에 없다.
            List<PartyMember> remaining = memberRepository.findActiveByPartyId(party.getId());
            PartyMember successor = remaining.get(0);
            successor.promoteToOwner();
            memberRepository.save(successor);
            party.changeOwner(successor.getUserId());
            newOwnerId = successor.getUserId();
        }

        if (party.getStatus() == PartyStatus.DISBANDED){
            invitationRepository.cancelAllPending(party.getId()); //// 없는 파티로 초대장이 살아있으면 안 된다
        }
        partyRepository.save(party);

        outboxRecorder.append(
                PartyAggregateType.PARTY_MEMBER,
                me.getId(),
                PartyEventType.PARTY_MEMBER_LEFT,
                userId,
                now,
                new PartyMemberLeftData(party.getId(), me.getId(), userId, party.getCurrentMember(),
                        newOwnerId, party.getStatus().name(), now));

        log.info("파티 퇴장. partyId={} userId={} role={} members={}/{} newOwnerId={} status={}",
                party.getId(), userId, me.getRole(), party.getCurrentMember(), party.getMaxMember(),
                newOwnerId, party.getStatus());
        return new LeaveResultView(party.getId(), party.getStatus(), party.getCurrentMember(), newOwnerId, now);
    }








}
