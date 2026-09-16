package com.workoutdone.rpgym.game.party.application;


import com.workoutdone.rpgym.game.outbox.application.OutboxRecorder;
import com.workoutdone.rpgym.game.outbox.domain.AggregateType;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;
import com.workoutdone.rpgym.game.party.application.payload.PartyInvitedData;
import com.workoutdone.rpgym.game.party.application.payload.PartyMemberJoinedData;
import com.workoutdone.rpgym.game.party.application.view.AcceptOutcome;
import com.workoutdone.rpgym.game.party.application.view.InvitationResultView;
import com.workoutdone.rpgym.game.party.application.view.InvitationView;
import com.workoutdone.rpgym.game.party.application.view.RejectResultView;
import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


///초대, 수락, 거절, 목록
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyInvitationService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository memberRepository;
    private final PartyInvitationRepository invitationRepository;
    private final MemberEnroller enroller;
    private final PartyCloser closer;
    private final OutboxRecorder outboxRecorder;
    private final PartyProperties props;
    private final Clock clock;



    ////초대 생성. 파티 단위 검증은 예외(전체 거절), 피초대자 단위는 결과값(부분 성공)
    @Transactional
    public List<InvitationResultView> invite(UUID inviterId, UUID partyId, List<UUID> inviteeIds) {
        if (inviteeIds == null || inviteeIds.isEmpty()) {
            throw new IllegalArgumentException("inviteeIds 는 비어 있을 수 없습니다.");
        }
        if (new HashSet<>(inviteeIds).size() != inviteeIds.size()) {
            throw new IllegalArgumentException("inviteeIds 에 중복이 있습니다.");
        }

        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.PARTY_NOT_FOUND));
        memberRepository.findActiveByPartyIdAndUserId(partyId, inviterId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.NOT_PARTY_MEMBER));

        Instant now = clock.instant();
        if (!party.isRecruiting(now)) {
            throw new PartyException(PartyErrorCode.PARTY_NOT_RECRUITING);
        }

        // PENDING 초대도 자리로 센다. 안 세면 빈 자리 1개에 초대장 3장이 나간다.
        long pending = invitationRepository.countPendingByPartyId(partyId);
        if (party.getCurrentMember() + pending + inviteeIds.size() > party.getMaxMember()) {
            log.debug("초대 거부 — 자리 부족. partyId={} members={}/{} pending={} requested={}",
                    partyId, party.getCurrentMember(), party.getMaxMember(), pending, inviteeIds.size());
            throw new PartyException(PartyErrorCode.PARTY_FULL);
        }

        List<InvitationResultView> results = new ArrayList<>(inviteeIds.size());
        for (UUID inviteeId : inviteeIds) {
            results.add(inviteOne(party, inviterId, inviteeId, now));
        }
        return results;
    }

    private InvitationResultView inviteOne(Party party, UUID inviterId, UUID inviteeId, Instant now) {

        if (inviteeId.equals(inviterId)){
            return skipped(party.getId(), inviteeId, InvitationResultView.Result.SELF_INVITE);
        }
        Optional<PartyMember> membership = memberRepository.findActiveByUserId(inviteeId);
        if (membership.isPresent()){
            boolean sameParty = membership.get().getPartyId().equals(party.getId());
            return skipped(party.getId(), inviteeId, sameParty ?
                    InvitationResultView.Result.ALREADY_MEMBER :
                    InvitationResultView.Result.ALREADY_IN_PARTY);
        }
        if (invitationRepository.existsPendingByPartyIdAndInviteeId(party.getId(), inviteeId)){
            return skipped(party.getId(), inviteeId, InvitationResultView.Result.ALREADY_INVITED);
        }

        PartyInvitation invitation = invitationRepository.save(PartyInvitation.create(
                UUID.randomUUID(), party.getId(), inviterId, inviteeId, now, props.invitationTtl()
        ));

        //// envelope.userId = invitee. 알림 서비스가 이 값으로 Push 대상을 정한다
        outboxRecorder.append(
                AggregateType.PARTY_INVITATION,
                invitation.getId(),
                OutboxEventType.PARTY_INVITED,
                inviteeId,
                now,
                new PartyInvitedData(invitation.getId(), party.getId(), party.getPartyName(),
                        inviterId, inviteeId, invitation.getExpiresAt())
        );

        log.info("파티 초대. invitationId={} partyId={} inviterId={} inviteeId={} expiresAt={}",
                invitation.getId(), party.getId(), inviterId, inviteeId, invitation.getExpiresAt());

        return InvitationResultView.created(inviteeId, invitation.getId(), invitation.getExpiresAt());
    }


    private InvitationResultView skipped(UUID partyId, UUID inviteeId, InvitationResultView.Result reason) {
        // 상태가 안 바뀌었으니 debug. 응답 본문에도 같은 reason 이 나간다.
        log.debug("파티 초대 건너뜀. partyId={} inviteeId={} reason={}", partyId, inviteeId, reason);
        return InvitationResultView.skipped(inviteeId, reason);
    }




    ////수락. 순서가 안정장치로,
    /// 1. 자리확보 (조건부 update) 실패 -> 초대 CANCELED 저장, FULL 반환(에외 아님. 저장을 살리려고)
    ///2. 초대 PENDING->ACCEPTED (조건부) 실패 -> 거절과 경합에서 짐. 예외 -> 1이 롤백돼 자리도 돌아간다
    ///3. 멤버 insert 유니크 위반 -> 409, 롤백
    /// 4. 4/4가 되면 마감(PartyCloser)
    @Transactional
    public AcceptOutcome accept(UUID userId, UUID invitationId){
        PartyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.isInvitee(userId)){
            throw new PartyException(PartyErrorCode.NOT_INVITEE);
        }

        Instant now = clock.instant();

        ///먁등: 이미 수락한 초대를 또 누르면 push 두번 탭 같은 경우 멤버를 또 만들지 않고 같은 답을 주게끔함.
        if (invitation.getStatus() == InvitationStatus.ACCEPTED){
            log.debug("초대 수락 재요청 - 멱등 응답. invitationId={} partyId={} userId={}",
                    invitationId, invitation.getPartyId(), userId);

            return alreadyJoined(invitation, userId);
        }

        if (!invitation.isAcceptable(now)){
            log.debug("초대 수락 거부 - PENDING 아님 또는 만료. invitationId={} status={} expires={}",
                    invitationId, invitation.getStatus(), invitation.getExpiresAt());
            throw new PartyException(PartyErrorCode.INVITATION_NOT_PENDING);
        }

        enroller.assertNotInParty(userId);

        UUID partyId = invitation.getPartyId();

        if (!partyRepository.reserveSeat(partyId, now)){
            invitationRepository.markCanceled(invitationId);
            //// 초대가 CANCELED 로 바뀌었으니 info. 원인(정원/마감)은 한 문장 UPDATE 라 여기서 모른다.
            log.info("초대 취소 — 정원 초과 또는 마감. invitationId={} partyId={} userId={}",
                    invitationId, partyId, userId);
            return new AcceptOutcome.Full(invitationId);
        }

        if (!invitationRepository.markAccepted(invitationId, now)) {
            log.debug("초대 수락 경합에서 밀림 — 롤백. invitationId={} partyId={}",
                    invitationId, partyId);
            throw new PartyException(PartyErrorCode.INVITATION_NOT_PENDING);   // 롤백 → reserveSeat 도 취소
        }

        PartyMember member = enroller.enroll(PartyMember.member(UUID.randomUUID(), partyId, userId, now));
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalStateException("자리를 확보한 파티가 없습니다: " + partyId));


        outboxRecorder.append(
                AggregateType.PARTY_MEMBER,
                member.getId(),
                OutboxEventType.PARTY_MEMBER_JOINED,
                userId,
                now,
                new PartyMemberJoinedData(partyId, member.getId(), userId, member.getRole().name(),
                        party.getCurrentMember(), party.getMaxMember(), now));


        //// 입장 로그는 마감 로그보다 먼저. 시간순으로 읽히게.
        log.info("파티 입장. partyId={} userId={} via=INVITATION invitationId={} members={}/{}",
                partyId, userId, invitationId, party.getCurrentMember(), party.getMaxMember());

        PartyStatus status = party.getStatus();
        if (party.isFull()) {
            closer.close(partyId, now, PartyCloser.Trigger.FULL);   // "파티 모집 마감. by=FULL" 은 저쪽이 남긴다
            status = PartyStatus.ACTIVE;
        }


        return new AcceptOutcome.Joined(invitationId, InvitationStatus.ACCEPTED, partyId, status,
                party.getCurrentMember(), party.getMaxMember(), now);

    }

    private AcceptOutcome alreadyJoined(PartyInvitation invitation, UUID userId) {
        Party party = partyRepository.findById(invitation.getPartyId())
                .orElseThrow(() -> new IllegalStateException("초대는 있는데 파티가 없습니다: " + invitation.getPartyId()));
        Instant joinedAt = memberRepository.findActiveByPartyIdAndUserId(party.getId(), userId)
                .map(PartyMember::getJoinedAt)
                .orElse(invitation.getRespondedAt());
        return new AcceptOutcome.Joined(invitation.getId(), InvitationStatus.ACCEPTED, party.getId(),
                party.displayStatus(clock.instant()), party.getCurrentMember(), party.getMaxMember(), joinedAt);
    }

    @Transactional
    public RejectResultView reject(UUID userId, UUID invitationId) {
        PartyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.isInvitee(userId)) {
            throw new PartyException(PartyErrorCode.NOT_INVITEE);
        }
        if (invitation.getStatus() == InvitationStatus.REJECTED) {
            log.debug("초대 거절 재요청 — 멱등 응답. invitationId={} userId={}", invitationId, userId);
            return new RejectResultView(invitationId, InvitationStatus.REJECTED, invitation.getRespondedAt());   // 멱등
        }

        Instant now = clock.instant();
        if (!invitation.isAcceptable(now) || !invitationRepository.markRejected(invitationId, now)) {
            log.debug("초대 거절 거부 — PENDING 아님 또는 만료. invitationId={} status={}", invitationId, invitation.getStatus());
            throw new PartyException(PartyErrorCode.INVITATION_NOT_PENDING);
        }
        log.info("파티 초대 거절. invitationId={} partyId={} userId={}", invitationId, invitation.getPartyId(), userId);
        return new RejectResultView(invitationId, InvitationStatus.REJECTED, now);
    }

    @Transactional(readOnly = true)
    public List<InvitationView> findPending(UUID userId) {
        List<PartyInvitation> invitations = invitationRepository.findPendingByInvitee(userId, clock.instant());
        if (invitations.isEmpty()) {
            return List.of();
        }
        Map<UUID, Party> parties = partyRepository.findAllByIds(
                        invitations.stream().map(PartyInvitation::getPartyId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Party::getId, Function.identity()));

        return invitations.stream().map(inv -> {
            Party p = parties.get(inv.getPartyId());
            return new InvitationView(inv.getId(), inv.getPartyId(), p.getPartyName(), inv.getInviterId(),
                    p.getCurrentMember(), p.getMaxMember(),
                    inv.getCreatedAt().toInstant(java.time.ZoneOffset.UTC), inv.getExpiresAt());
        }).toList();
    }
}