package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.application.payload.PartyInvitationClosedData;
import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;
import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 초대의 끝을 알리는 이벤트 규칙. 슬랙 버튼을 거둘 신호가 이것뿐이라
 * "닫혔으면 반드시 나간다 / 안 닫혔으면 절대 안 나간다" 두 가지가 핵심이다.
 */
class PartyInvitationCloserTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);

    private PartyInvitationRepository invitationRepository;
    private PartyOutboxRecorder outboxRecorder;
    private PartyInvitationCloser sut;

    private Party party;
    private PartyInvitation invitation;
    private UUID invitee;

    @BeforeEach
    void setUp() {
        invitationRepository = mock(PartyInvitationRepository.class);
        outboxRecorder = mock(PartyOutboxRecorder.class);
        sut = new PartyInvitationCloser(invitationRepository, outboxRecorder);

        UUID inviter = UUID.randomUUID();
        invitee = UUID.randomUUID();
        party = Party.create(UUID.randomUUID(), "부산 걷기", inviter, PartyVisibility.PRIVATE, PartyMetric.STEPS,
                4, NOW, Duration.ofHours(24), Duration.ofDays(7));
        invitation = PartyInvitation.create(UUID.randomUUID(), party.getId(), inviter, invitee, NOW, TTL);
    }

    @DisplayName("만료: EXPIRED 로 전이하고 invitee 앞으로 CLOSED 를 싣는다")
    @Test
    void expire() {
        given(invitationRepository.markExpired(invitation.getId())).willReturn(true);

        assertThat(sut.close(invitation, party.getPartyName(), InvitationCloseReason.EXPIRED, NOW)).isTrue();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxRecorder).append(eq(PartyAggregateType.PARTY_INVITATION), eq(invitation.getId()),
                eq(PartyEventType.PARTY_INVITATION_CLOSED), eq(invitee), eq(NOW), payload.capture());

        PartyInvitationClosedData data = (PartyInvitationClosedData) payload.getValue();
        assertThat(data.invitationId()).isEqualTo(invitation.getId());
        assertThat(data.partyName()).isEqualTo("부산 걷기");
        assertThat(data.inviteeId()).isEqualTo(invitee);
        assertThat(data.reason()).isEqualTo(InvitationCloseReason.EXPIRED.name());
        assertThat(data.status()).isEqualTo(InvitationStatus.EXPIRED.name());
    }

    @DisplayName("전이에 실패하면(그 사이 유저가 응답) CLOSED 를 싣지 않는다 — 초대당 한 번만 나가야 한다")
    @Test
    void skipWhenAlreadyResponded() {
        given(invitationRepository.markExpired(invitation.getId())).willReturn(false);

        assertThat(sut.close(invitation, party.getPartyName(), InvitationCloseReason.EXPIRED, NOW)).isFalse();

        verify(outboxRecorder, never()).append(any(), any(), any(), any(), any(), any());
    }

    @DisplayName("파티 마감: 남은 PENDING 은 CANCELED 로 닫히고 reason 은 PARTY_CLOSED 다")
    @Test
    void closeAllPending() {
        PartyInvitation other = PartyInvitation.create(UUID.randomUUID(), party.getId(),
                party.getOwnerId(), UUID.randomUUID(), NOW, TTL);
        given(invitationRepository.findPendingByPartyId(party.getId())).willReturn(List.of(invitation, other));
        given(invitationRepository.markCanceled(any())).willReturn(true);

        assertThat(sut.closeAllPending(party, InvitationCloseReason.PARTY_CLOSED, NOW)).isEqualTo(2);

        verify(outboxRecorder).append(any(), eq(invitation.getId()),
                eq(PartyEventType.PARTY_INVITATION_CLOSED), eq(invitee), eq(NOW), any());
        verify(outboxRecorder).append(any(), eq(other.getId()),
                eq(PartyEventType.PARTY_INVITATION_CLOSED), eq(other.getInviteeId()), eq(NOW), any());
    }

    @DisplayName("파티 마감: 한 건이 그 사이 수락됐으면 그 건만 빠진다")
    @Test
    void closeAllPendingSkipsResponded() {
        PartyInvitation accepted = PartyInvitation.create(UUID.randomUUID(), party.getId(),
                party.getOwnerId(), UUID.randomUUID(), NOW, TTL);
        given(invitationRepository.findPendingByPartyId(party.getId())).willReturn(List.of(invitation, accepted));
        given(invitationRepository.markCanceled(invitation.getId())).willReturn(true);
        given(invitationRepository.markCanceled(accepted.getId())).willReturn(false);

        assertThat(sut.closeAllPending(party, InvitationCloseReason.PARTY_CLOSED, NOW)).isEqualTo(1);

        verify(outboxRecorder, never()).append(any(), eq(accepted.getId()), any(), any(), any(), any());
    }
}
