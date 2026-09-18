package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.application.view.LeaveResultView;
import com.workoutdone.rpgym.game.party.application.view.PartyView;
import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;
import com.workoutdone.rpgym.game.party.domain.MemberRole;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

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

class PartyCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final PartyProperties PROPS = new PartyProperties(
            4, Duration.ofHours(24), Duration.ofDays(7), Duration.ofHours(24), 10);

    private PartyRepository partyRepository;
    private PartyMemberRepository memberRepository;
    private PartyInvitationCloser invitationCloser;
    private PartyOutboxRecorder outboxRecorder;
    private ApplicationEventPublisher events;
    private PartyCommandService sut;

    @BeforeEach
    void setUp() {
        partyRepository = mock(PartyRepository.class);
        memberRepository = mock(PartyMemberRepository.class);
        invitationCloser = mock(PartyInvitationCloser.class);
        outboxRecorder = mock(PartyOutboxRecorder.class);
        events = mock(ApplicationEventPublisher.class);
        sut = new PartyCommandService(partyRepository, memberRepository, invitationCloser,
                new MemberEnroller(memberRepository), mock(PartyCloser.class), outboxRecorder, events,
                PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
        given(partyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(memberRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    // ───────────── 생성 = 파티장의 파티 퀘스트 생성 요청 ─────────────

    @DisplayName("생성: RECRUITING 1/4, 생성자가 OWNER, metric 확정, PartyQuestRequested 를 발행한다")
    @Test
    void createRequestsPartyQuest() {
        UUID owner = UUID.randomUUID();
        given(memberRepository.findActiveByUserId(owner)).willReturn(Optional.empty());

        PartyView view = sut.create(owner, "아침 걷기", null, PartyMetric.STEPS);

        assertThat(view.status()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(view.visibility()).isEqualTo(PartyVisibility.PRIVATE);      // null 이면 PRIVATE
        assertThat(view.metric()).isEqualTo(PartyMetric.STEPS);
        assertThat(view.memberCount()).isEqualTo(1);
        assertThat(view.members()).singleElement()
                .satisfies(m -> {
                    assertThat(m.userId()).isEqualTo(owner);
                    assertThat(m.role()).isEqualTo(MemberRole.OWNER);
                });

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PartyQuestRequested.class);
        PartyQuestRequested requested = (PartyQuestRequested) captor.getValue();
        assertThat(requested.partyId()).isEqualTo(view.partyId());
        assertThat(requested.ownerId()).isEqualTo(owner);
        assertThat(requested.metric()).isEqualTo(PartyMetric.STEPS);
        assertThat(requested.createdAt()).isEqualTo(NOW);
        assertThat(requested.matchingDeadlineAt()).isEqualTo(NOW.plus(PROPS.recruitDuration()));
        assertThat(requested.endsAt()).isEqualTo(NOW.plus(PROPS.lifetime()));

        // 생성 자체는 Kafka 로 안 나간다. 알림은 초대 · 마감 · 종료 때만.
        verify(outboxRecorder, never()).append(any(), any(), any(), any(), any(), any());
    }

    @DisplayName("생성: 이미 파티가 있으면 409 ALREADY_IN_PARTY 이고 아무것도 저장 · 발행하지 않는다")
    @Test
    void createWhenAlreadyInParty() {
        UUID owner = UUID.randomUUID();
        given(memberRepository.findActiveByUserId(owner))
                .willReturn(Optional.of(PartyMember.member(UUID.randomUUID(), UUID.randomUUID(), owner, NOW)));

        assertThatThrownBy(() -> sut.create(owner, "x", PartyVisibility.PUBLIC, PartyMetric.STEPS))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.ALREADY_IN_PARTY);
        verify(partyRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @DisplayName("생성: metric 이 null 이면 도메인이 거부한다 — 컨트롤러 @NotNull 을 우회해도 막힌다")
    @Test
    void createWithoutMetric() {
        UUID owner = UUID.randomUUID();
        given(memberRepository.findActiveByUserId(owner)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.create(owner, "x", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(events, never()).publishEvent(any());
    }

    // ───────────── 탈퇴 ─────────────

    @DisplayName("파티장이 나가면 joined_at 이 가장 오래된 멤버가 파티장이 되고 LEFT 이벤트에 newOwnerId 가 실린다")
    @Test
    void ownerLeaveSucceeds() {
        UUID owner = UUID.randomUUID();
        UUID oldest = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        // 생성 1 + 합류 2 = 3. 프로덕션에선 reserveSeat 이 올렸을 값이라 restore 로 만든다.
        Party party = Party.restore(UUID.randomUUID(), "p", owner, PartyStatus.RECRUITING, PartyVisibility.PRIVATE,
                PartyMetric.STEPS, 4, 3, NOW.plus(PROPS.recruitDuration()), NOW.plus(PROPS.lifetime()));
        PartyMember ownerRow = PartyMember.owner(UUID.randomUUID(), party.getId(), owner, NOW);
        PartyMember oldestRow = PartyMember.member(UUID.randomUUID(), party.getId(), oldest, NOW.plusSeconds(10));
        PartyMember newerRow = PartyMember.member(UUID.randomUUID(), party.getId(), newer, NOW.plusSeconds(20));

        given(memberRepository.findActiveByUserId(owner)).willReturn(Optional.of(ownerRow));
        given(partyRepository.findByIdForUpdate(party.getId())).willReturn(Optional.of(party));
        given(memberRepository.findActiveByPartyId(party.getId())).willReturn(List.of(oldestRow, newerRow));

        LeaveResultView result = sut.leave(owner);

        assertThat(result.newOwnerId()).isEqualTo(oldest);
        assertThat(result.memberCount()).isEqualTo(2);
        assertThat(oldestRow.getRole()).isEqualTo(MemberRole.OWNER);
        assertThat(party.getOwnerId()).isEqualTo(oldest);
        assertThat(ownerRow.isActive()).isFalse();
        verify(outboxRecorder).append(any(), eq(ownerRow.getId()), eq(PartyEventType.PARTY_MEMBER_LEFT), eq(owner), any(), any());
    }

    @DisplayName("마지막 멤버가 나가면 DISBANDED 이고 남은 PENDING 초대가 취소된다")
    @Test
    void lastMemberLeaveDisbands() {
        UUID owner = UUID.randomUUID();
        Party party = Party.create(UUID.randomUUID(), "p", owner, PartyVisibility.PRIVATE, PartyMetric.STEPS, 4, NOW,
                PROPS.recruitDuration(), PROPS.lifetime());
        PartyMember ownerRow = PartyMember.owner(UUID.randomUUID(), party.getId(), owner, NOW);
        given(memberRepository.findActiveByUserId(owner)).willReturn(Optional.of(ownerRow));
        given(partyRepository.findByIdForUpdate(party.getId())).willReturn(Optional.of(party));

        LeaveResultView result = sut.leave(owner);

        assertThat(result.partyStatus()).isEqualTo(PartyStatus.DISBANDED);
        assertThat(result.memberCount()).isZero();
        // 초대를 거두는 것만으로는 부족하다 — 받은 사람 슬랙의 버튼을 거둘 이벤트까지 나가야 한다.
        verify(invitationCloser).closeAllPending(eq(party), eq(InvitationCloseReason.PARTY_DISBANDED), any());
    }

    @DisplayName("소속 파티가 없으면 404 NOT_IN_PARTY")
    @Test
    void leaveWithoutParty() {
        UUID user = UUID.randomUUID();
        given(memberRepository.findActiveByUserId(user)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.leave(user))
                .isInstanceOf(PartyException.class)
                .extracting(e -> ((PartyException) e).getErrorCode())
                .isEqualTo(PartyErrorCode.NOT_IN_PARTY);
    }
}
