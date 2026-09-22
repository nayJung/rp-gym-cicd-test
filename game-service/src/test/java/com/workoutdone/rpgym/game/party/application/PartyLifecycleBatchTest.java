package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 초대 만료 배치. 벌크 UPDATE 가 아니라 건별로 닫는 이유(= 건마다 CLOSED 를 실어야 한다)를 고정한다. */
class PartyLifecycleBatchTest {

    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");
    private static final Instant CREATED = NOW.minus(Duration.ofHours(25));

    private PartyRepository partyRepository;
    private PartyInvitationRepository invitationRepository;
    private PartyInvitationCloser invitationCloser;
    private PartyLifecycleBatch sut;

    private Party party;

    @BeforeEach
    void setUp() {
        partyRepository = mock(PartyRepository.class);
        invitationRepository = mock(PartyInvitationRepository.class);
        invitationCloser = mock(PartyInvitationCloser.class);
        sut = new PartyLifecycleBatch(partyRepository, mock(PartyMemberRepository.class), invitationRepository,
                mock(PartyCloser.class), invitationCloser, mock(PartyOutboxRecorder.class),
                mock(ApplicationEventPublisher.class), Clock.fixed(NOW, ZoneOffset.UTC));

        party = Party.create(UUID.randomUUID(), "부산 걷기", UUID.randomUUID(), PartyVisibility.PRIVATE,
                PartyMetric.STEPS, 4, CREATED, Duration.ofHours(24), Duration.ofDays(7));
    }

    private PartyInvitation expired() {
        return PartyInvitation.create(UUID.randomUUID(), party.getId(), party.getOwnerId(),
                UUID.randomUUID(), CREATED, Duration.ofHours(24));
    }

    @DisplayName("만료된 초대를 건별로 닫는다 — 파티 이름은 한 번만 조회한다")
    @Test
    void expireInvitations() {
        PartyInvitation a = expired();
        PartyInvitation b = expired();
        given(invitationRepository.findPendingExpired(eq(NOW), anyInt())).willReturn(List.of(a, b));
        given(partyRepository.findAllByIds(any())).willReturn(List.of(party));
        given(invitationCloser.close(any(), any(), eq(InvitationCloseReason.EXPIRED), eq(NOW))).willReturn(true);

        assertThat(sut.expireInvitations()).isEqualTo(2);

        verify(invitationCloser).close(a, "부산 걷기", InvitationCloseReason.EXPIRED, NOW);
        verify(invitationCloser).close(b, "부산 걷기", InvitationCloseReason.EXPIRED, NOW);
        verify(partyRepository).findAllByIds(any());   // 같은 파티 2건 → 조회 1회
    }

    @DisplayName("만료 대상이 없으면 파티를 읽지도 않는다")
    @Test
    void nothingToExpire() {
        given(invitationRepository.findPendingExpired(eq(NOW), anyInt())).willReturn(List.of());

        assertThat(sut.expireInvitations()).isZero();

        verify(partyRepository, never()).findAllByIds(any());
    }

    @DisplayName("그 사이 유저가 응답한 건은 세지 않는다")
    @Test
    void skipsRespondedDuringRound() {
        PartyInvitation a = expired();
        PartyInvitation responded = expired();
        given(invitationRepository.findPendingExpired(eq(NOW), anyInt())).willReturn(List.of(a, responded));
        given(partyRepository.findAllByIds(any())).willReturn(List.of(party));
        given(invitationCloser.close(eq(a), any(), any(), any())).willReturn(true);
        given(invitationCloser.close(eq(responded), any(), any(), any())).willReturn(false);

        assertThat(sut.expireInvitations()).isEqualTo(1);
    }
}
