package com.workoutdone.rpgym.game.party.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.game.party.application.PartyErrorCode;
import com.workoutdone.rpgym.game.party.application.PartyException;
import com.workoutdone.rpgym.game.party.application.PartyInvitationService;
import com.workoutdone.rpgym.game.party.application.view.AcceptOutcome;
import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 슬랙 버튼의 응답을 이벤트로 받는 경로.
 *
 * 여기서 예외를 던지면 offset 이 안 밀려 그 파티션이 영원히 막힌다. 그래서 테스트의 대부분은
 * "이 입력에 예외를 던지지 않는다" 를 본다. 재시도해서 될 일(DB 장애)만 던져야 한다.
 */
class NotificationEventConsumerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID INVITATION = UUID.randomUUID();

    private PartyInvitationService invitationService;
    private NotificationEventConsumer sut;

    @BeforeEach
    void setUp() {
        invitationService = mock(PartyInvitationService.class);
        sut = new NotificationEventConsumer(new ObjectMapper(), invitationService);
    }

    private String message(String eventType, String outcome) {
        return """
                {"eventId":"%s","eventType":"%s","userId":"%s",
                 "data":{"invitationId":"%s","outcome":"%s"}}
                """.formatted(UUID.randomUUID(), eventType, USER, INVITATION, outcome);
    }

    @DisplayName("ACCEPTED 면 수락을 부른다")
    @Test
    void accept() {
        given(invitationService.accept(USER, INVITATION)).willReturn(
                new AcceptOutcome.Joined(INVITATION, InvitationStatus.ACCEPTED, UUID.randomUUID(),
                        PartyStatus.RECRUITING, 2, 4, Instant.now()));

        sut.consume(message("PARTY_INVITATION_RESPONDED", "ACCEPTED"));

        verify(invitationService).accept(USER, INVITATION);
    }

    @DisplayName("REJECTED 면 거절을 부른다")
    @Test
    void reject() {
        sut.consume(message("PARTY_INVITATION_RESPONDED", "REJECTED"));

        verify(invitationService).reject(USER, INVITATION);
    }

    @DisplayName("정원이 차서 Full 이 와도 정상 ack — 결과는 PARTY_FULL CLOSED 로 이미 나갔다")
    @Test
    void full() {
        given(invitationService.accept(USER, INVITATION)).willReturn(new AcceptOutcome.Full(INVITATION));

        assertThatCode(() -> sut.consume(message("PARTY_INVITATION_RESPONDED", "ACCEPTED")))
                .doesNotThrowAnyException();
    }

    @DisplayName("이미 닫힌 초대는 삼킨다 — 재시도해도 같은 결과라 던지면 파티션이 막힌다")
    @Test
    void alreadyClosed() {
        willThrow(new PartyException(PartyErrorCode.INVITATION_NOT_PENDING))
                .given(invitationService).accept(USER, INVITATION);

        assertThatCode(() -> sut.consume(message("PARTY_INVITATION_RESPONDED", "ACCEPTED")))
                .doesNotThrowAnyException();
    }

    @DisplayName("슬랙 유저 매핑이 틀려 NOT_INVITEE 여도 삼킨다")
    @Test
    void notInvitee() {
        willThrow(new PartyException(PartyErrorCode.NOT_INVITEE))
                .given(invitationService).reject(USER, INVITATION);

        assertThatCode(() -> sut.consume(message("PARTY_INVITATION_RESPONDED", "REJECTED")))
                .doesNotThrowAnyException();
    }

    @DisplayName("파티가 모르는 eventType(퀘스트 응답 등)은 조용히 ack 한다 — 같은 토픽을 공유한다")
    @Test
    void otherEventType() {
        sut.consume(message("QUEST_OFFER_RESPONDED", "ACCEPTED"));

        verify(invitationService, never()).accept(any(), any());
        verify(invitationService, never()).reject(any(), any());
    }

    @DisplayName("알 수 없는 outcome 은 아무것도 하지 않는다")
    @Test
    void unknownOutcome() {
        sut.consume(message("PARTY_INVITATION_RESPONDED", "MAYBE"));

        verify(invitationService, never()).accept(any(), any());
        verify(invitationService, never()).reject(any(), any());
    }

    @DisplayName("깨진 JSON · 필수 필드 누락은 건너뛴다")
    @Test
    void malformed() {
        assertThatCode(() -> sut.consume("{ not json")).doesNotThrowAnyException();
        assertThatCode(() -> sut.consume("""
                {"eventId":"%s","eventType":"PARTY_INVITATION_RESPONDED","userId":"%s","data":{"outcome":"ACCEPTED"}}
                """.formatted(UUID.randomUUID(), USER))).doesNotThrowAnyException();

        verify(invitationService, never()).accept(any(), any());
    }

    @DisplayName("DB 장애처럼 다시 하면 되는 것은 던져서 재시도시킨다")
    @Test
    void retryable() {
        willThrow(new IllegalStateException("db down")).given(invitationService).accept(USER, INVITATION);

        assertThatCode(() -> sut.consume(message("PARTY_INVITATION_RESPONDED", "ACCEPTED")))
                .isInstanceOf(IllegalStateException.class);
    }
}
