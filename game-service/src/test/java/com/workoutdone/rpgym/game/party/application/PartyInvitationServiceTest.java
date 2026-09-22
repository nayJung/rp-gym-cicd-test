package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.application.view.AcceptOutcome;
import com.workoutdone.rpgym.game.party.application.view.InvitationResultView;
import com.workoutdone.rpgym.game.party.application.view.RejectResultView;
import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;
import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 초대 · 수락 · 거절의 판정 규칙. 저장소는 전부 Mock — 조건부 UPDATE 의 성공/실패를 given 으로 흉내 낸다.
 * 진짜 경합은 PartySeatConcurrencyTest 가 실제 PostgreSQL 에서 증명한다.
 */
class PartyInvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final PartyProperties PROPS = new PartyProperties(
            4, Duration.ofHours(24), Duration.ofDays(7), Duration.ofHours(24), 10);

    private PartyRepository partyRepository;
    private PartyMemberRepository memberRepository;
    private PartyInvitationRepository invitationRepository;
    private PartyCloser closer;
    private PartyInvitationCloser invitationCloser;
    private PartyOutboxRecorder outboxRecorder;
    private PartyInvitationService sut;

    private UUID inviter;
    private UUID invitee;
    private Party party;
    private PartyInvitation invitation;

    @BeforeEach
    void setUp() {
        partyRepository = mock(PartyRepository.class);
        memberRepository = mock(PartyMemberRepository.class);
        invitationRepository = mock(PartyInvitationRepository.class);
        closer = mock(PartyCloser.class);
        invitationCloser = mock(PartyInvitationCloser.class);
        outboxRecorder = mock(PartyOutboxRecorder.class);
        sut = new PartyInvitationService(partyRepository, memberRepository, invitationRepository,
                new MemberEnroller(memberRepository), closer, invitationCloser, outboxRecorder, PROPS,
                Clock.fixed(NOW, ZoneOffset.UTC));

        inviter = UUID.randomUUID();
        invitee = UUID.randomUUID();
        party = Party.create(UUID.randomUUID(), "p", inviter, PartyVisibility.PRIVATE, PartyMetric.STEPS, 4, NOW,
                PROPS.recruitDuration(), PROPS.lifetime());
        invitation = PartyInvitation.create(UUID.randomUUID(), party.getId(), inviter, invitee, NOW, PROPS.invitationTtl());

        given(invitationRepository.findById(invitation.getId())).willReturn(Optional.of(invitation));
        given(partyRepository.findById(party.getId())).willReturn(Optional.of(party));
        given(memberRepository.findActiveByUserId(invitee)).willReturn(Optional.empty());
        given(memberRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    // ───────────── 수락 ─────────────

    @DisplayName("수락: 자리 확보 → 초대 ACCEPTED → 멤버 INSERT → JOINED 발행 (받는 사람 = 수락한 본인)")
    @Test
    void accept() {
        given(partyRepository.reserveSeat(party.getId(), NOW)).willReturn(true);
        given(invitationRepository.markAccepted(invitation.getId(), NOW)).willReturn(true);

        AcceptOutcome outcome = sut.accept(invitee, invitation.getId());

        assertThat(outcome).isInstanceOf(AcceptOutcome.Joined.class);
        AcceptOutcome.Joined joined = (AcceptOutcome.Joined) outcome;
        assertThat(joined.partyId()).isEqualTo(party.getId());
        assertThat(joined.invitationStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(memberRepository).save(any(PartyMember.class));
        verify(outboxRecorder).append(any(), any(), eq(PartyEventType.PARTY_MEMBER_JOINED), eq(invitee), any(), any());
        // 슬랙 버튼을 결과로 바꾸는 신호. JOINED 는 매칭 · 생성으로도 나가서 invitationId 를 못 싣는다.
        verify(invitationCloser).recordClosed(eq(invitation), eq(party.getPartyName()),
                eq(InvitationCloseReason.ACCEPTED), eq(NOW));
        verify(closer, never()).close(any(), any(), any());     // 1/4 → 2/4, 아직 안 찼다
    }

    @DisplayName("수락: 자리가 없으면 초대를 CANCELED 로 바꾸고 Full 을 돌려준다 (예외 아님 — 저장을 살려야 한다)")
    @Test
    void acceptWhenFull() {
        given(partyRepository.reserveSeat(party.getId(), NOW)).willReturn(false);
        given(invitationRepository.markCanceled(invitation.getId())).willReturn(true);

        AcceptOutcome outcome = sut.accept(invitee, invitation.getId());

        assertThat(outcome).isInstanceOf(AcceptOutcome.Full.class);
        verify(invitationRepository).markCanceled(invitation.getId());
        verify(memberRepository, never()).save(any());
        // 눌렀는데 실패했다는 걸 슬랙에 보여줄 수단이 이 이벤트뿐이다 (REST 응답이 없는 경로다)
        verify(invitationCloser).recordClosed(eq(invitation), eq(party.getPartyName()),
                eq(InvitationCloseReason.PARTY_FULL), eq(NOW));
        verify(outboxRecorder, never()).append(any(), any(), any(), any(), any(), any());
    }

    @DisplayName("수락: 자리도 없고 초대도 이미 닫혔으면 CLOSED 를 싣지 않는다 — 초대당 CLOSED 는 한 번뿐이다")
    @Test
    void acceptWhenFullAndAlreadyClosed() {
        given(partyRepository.reserveSeat(party.getId(), NOW)).willReturn(false);
        given(invitationRepository.markCanceled(invitation.getId())).willReturn(false);

        assertThat(sut.accept(invitee, invitation.getId())).isInstanceOf(AcceptOutcome.Full.class);

        verify(invitationCloser, never()).recordClosed(any(), any(), any(), any());
    }

    @DisplayName("수락: 거절과 경합해서 초대 전이에 지면 예외 → 롤백으로 자리도 돌아간다")
    @Test
    void acceptLosesToReject() {
        given(partyRepository.reserveSeat(party.getId(), NOW)).willReturn(true);
        given(invitationRepository.markAccepted(invitation.getId(), NOW)).willReturn(false);

        assertThatThrownBy(() -> sut.accept(invitee, invitation.getId()))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.INVITATION_NOT_PENDING);
        verify(memberRepository, never()).save(any());
    }

    @DisplayName("수락: 초대받은 사람이 아니면 403 NOT_INVITEE")
    @Test
    void acceptByStranger() {
        assertThatThrownBy(() -> sut.accept(UUID.randomUUID(), invitation.getId()))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.NOT_INVITEE);
    }

    @DisplayName("수락: 만료된 초대는 409 INVITATION_NOT_PENDING")
    @Test
    void acceptExpired() {
        PartyInvitation expired = PartyInvitation.create(UUID.randomUUID(), party.getId(), inviter, invitee,
                NOW.minus(Duration.ofHours(25)), PROPS.invitationTtl());   // expires_at = NOW - 1h
        given(invitationRepository.findById(expired.getId())).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> sut.accept(invitee, expired.getId()))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.INVITATION_NOT_PENDING);
        verify(partyRepository, never()).reserveSeat(any(), any());
    }

    @DisplayName("수락: 이미 다른 파티에 있으면 409 ALREADY_IN_PARTY — 자리를 잡기 전에 막는다")
    @Test
    void acceptWhileInAnotherParty() {
        given(memberRepository.findActiveByUserId(invitee))
                .willReturn(Optional.of(PartyMember.member(UUID.randomUUID(), UUID.randomUUID(), invitee, NOW)));

        assertThatThrownBy(() -> sut.accept(invitee, invitation.getId()))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.ALREADY_IN_PARTY);
        verify(partyRepository, never()).reserveSeat(any(), any());
    }

    @DisplayName("수락: 이미 ACCEPTED 인 초대를 또 누르면 멤버를 다시 만들지 않고 같은 Joined 를 준다 (멱등)")
    @Test
    void acceptTwiceIsIdempotent() {
        // 첫 수락을 실제로 통과시켜 ACCEPTED 로 만든다
        given(partyRepository.reserveSeat(party.getId(), NOW)).willReturn(true);
        given(invitationRepository.markAccepted(invitation.getId(), NOW)).willReturn(true);
        sut.accept(invitee, invitation.getId());
        // markAccepted 는 조건부 UPDATE 한 문장이라 엔티티에 setter 가 없다. DB 가 바꿨을 상태를 직접 맞춘다.
        ReflectionTestUtils.setField(invitation, "status", InvitationStatus.ACCEPTED);
        ReflectionTestUtils.setField(invitation, "respondedAt", NOW);
        given(memberRepository.findActiveByPartyIdAndUserId(party.getId(), invitee))
                .willReturn(Optional.of(PartyMember.member(UUID.randomUUID(), party.getId(), invitee, NOW)));

        AcceptOutcome second = sut.accept(invitee, invitation.getId());

        assertThat(second).isInstanceOf(AcceptOutcome.Joined.class);
        verify(memberRepository).save(any(PartyMember.class));            // 정확히 한 번
        verify(partyRepository).reserveSeat(party.getId(), NOW);          // 정확히 한 번
    }

    // ───────────── 거절 ─────────────

    @DisplayName("거절: PENDING → REJECTED. 이벤트는 발행하지 않는다 (알림 쪽에 줄 게 없다)")
    @Test
    void reject() {
        given(invitationRepository.markRejected(invitation.getId(), NOW)).willReturn(true);

        RejectResultView result = sut.reject(invitee, invitation.getId());

        assertThat(result.invitationStatus()).isEqualTo(InvitationStatus.REJECTED);
        assertThat(result.responedAt()).isEqualTo(NOW);
        // 거절도 슬랙 버튼을 거둬야 한다. 수락과 같은 이벤트, reason 만 다르다.
        verify(invitationCloser).recordClosed(eq(invitation), eq(party.getPartyName()),
                eq(InvitationCloseReason.REJECTED), eq(NOW));
        verify(outboxRecorder, never()).append(any(), any(), any(), any(), any(), any());
    }

    @DisplayName("거절: 초대받은 사람이 아니면 403 NOT_INVITEE")
    @Test
    void rejectByStranger() {
        assertThatThrownBy(() -> sut.reject(UUID.randomUUID(), invitation.getId()))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.NOT_INVITEE);
    }

    @DisplayName("거절: 수락과 경합해서 전이에 지면 409 INVITATION_NOT_PENDING")
    @Test
    void rejectLosesToAccept() {
        given(invitationRepository.markRejected(invitation.getId(), NOW)).willReturn(false);

        assertThatThrownBy(() -> sut.reject(invitee, invitation.getId()))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.INVITATION_NOT_PENDING);
    }

    // ───────────── 초대 ─────────────

    @DisplayName("초대: 자기 자신은 SELF_INVITE 로 건너뛰고 나머지는 CREATED + INVITED 발행 (받는 사람 = 초대받은 사람)")
    @Test
    void invitePartialSuccess() {
        given(memberRepository.findActiveByPartyIdAndUserId(party.getId(), inviter))
                .willReturn(Optional.of(PartyMember.owner(UUID.randomUUID(), party.getId(), inviter, NOW)));
        given(invitationRepository.countPendingByPartyId(party.getId())).willReturn(0L);
        given(invitationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        List<InvitationResultView> results = sut.invite(inviter, party.getId(), List.of(inviter, invitee));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).result()).isEqualTo(InvitationResultView.Result.SELF_INVITE);
        assertThat(results.get(1).result()).isEqualTo(InvitationResultView.Result.CREATED);
        verify(outboxRecorder).append(any(), any(), eq(PartyEventType.PARTY_INVITED), eq(invitee), any(), any());
    }

    @DisplayName("초대: 현재 인원 + PENDING + 요청 인원이 정원을 넘으면 전체 거절 PARTY_FULL")
    @Test
    void inviteOverCapacity() {
        given(memberRepository.findActiveByPartyIdAndUserId(party.getId(), inviter))
                .willReturn(Optional.of(PartyMember.owner(UUID.randomUUID(), party.getId(), inviter, NOW)));
        given(invitationRepository.countPendingByPartyId(party.getId())).willReturn(2L);   // 1 + 2 + 2 = 5 > 4

        assertThatThrownBy(() -> sut.invite(inviter, party.getId(), List.of(invitee, UUID.randomUUID())))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.PARTY_FULL);
        verify(invitationRepository, never()).save(any());
    }

    @DisplayName("초대: 파티 멤버가 아니면 403 NOT_PARTY_MEMBER")
    @Test
    void inviteByNonMember() {
        UUID stranger = UUID.randomUUID();
        given(memberRepository.findActiveByPartyIdAndUserId(party.getId(), stranger)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.invite(stranger, party.getId(), List.of(invitee)))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.NOT_PARTY_MEMBER);
    }

    @DisplayName("초대: 같은 사람에게 PENDING 초대가 이미 있으면 ALREADY_INVITED 로 건너뛴다")
    @Test
    void inviteDuplicate() {
        given(memberRepository.findActiveByPartyIdAndUserId(party.getId(), inviter))
                .willReturn(Optional.of(PartyMember.owner(UUID.randomUUID(), party.getId(), inviter, NOW)));
        given(invitationRepository.countPendingByPartyId(party.getId())).willReturn(1L);
        given(invitationRepository.existsPendingByPartyIdAndInviteeId(party.getId(), invitee)).willReturn(true);

        List<InvitationResultView> results = sut.invite(inviter, party.getId(), List.of(invitee));

        assertThat(results.get(0).result()).isEqualTo(InvitationResultView.Result.ALREADY_INVITED);
        verify(invitationRepository, never()).save(any());
    }
}
