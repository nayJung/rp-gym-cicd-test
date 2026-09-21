package com.workoutdone.rpgym.game.party.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.game.party.adapter.in.kafka.dto.NotificationEventEnvelope;
import com.workoutdone.rpgym.game.party.adapter.in.kafka.dto.PartyInvitationRespondedData;
import com.workoutdone.rpgym.game.party.application.PartyException;
import com.workoutdone.rpgym.game.party.application.PartyInvitationService;
import com.workoutdone.rpgym.game.party.application.view.AcceptOutcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [존폐 미정 — #122] 슬랙 버튼 응답을 게임으로 넘기는 방식이 도메인마다 갈려 있다.
 * 파티는 이 컨슈머(Kafka), 퀘스트는 수락 REST API 다. 알림 쪽이 양쪽을 다 구현하게 되므로
 * 하나로 통일해야 한다. REST 로 정해지면 이 클래스와 notification.events 구독을 제거하고
 * PartyInvitationController 의 accept/reject 가 슬랙 경로까지 겸한다.
 * Kafka 로 정해지면 이대로 두고 퀘스트 쪽이 컨슈머를 추가한다.
 * 어느 쪽이든 PartyInvitationService.accept()/reject() 로 수렴하므로 판정 로직은 바뀌지 않는다.
 *
 * ──────────────────────────────────────────────────────────────────────
 * notification.events 에서 슬랙 버튼의 응답을 받는다.
 *
 * 슬랙 → Notification → 이벤트 → 여기 → 상태 변경 → PARTY_INVITATION_CLOSED → Notification → 슬랙.
 * 유저에게 결과를 보여주는 건 이 컨슈머가 아니라 그 CLOSED 이벤트다. 그래서 여기서는
 * 실패를 응답으로 돌려줄 곳이 없고, "무엇을 재시도하고 무엇을 삼킬지" 가 유일한 판단이다.
 *
 *   던진다   -> offset 미커밋 -> 재시도. DB 다운처럼 "다시 하면 되는 것"
 *   안 던진다 -> 정상 ack.     이미 닫힌 초대 · 정원 초과처럼 "몇 번을 해도 같은 결과인 것"
 *
 * 계약 위반에 예외를 던지면 그 파티션이 영원히 막힌다. HealthEventConsumer 와 같은 규칙이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    /**
     * Notification 과 맞춰야 하는 값이다 (잠정값). quest 의 QUEST_OFFER_RESPONDED 와 짝이다.
     * 바뀌면 여기만 고치면 된다.
     */
    private static final String PARTY_INVITATION_RESPONDED = "PARTY_INVITATION_RESPONDED";

    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";

    private final ObjectMapper objectMapper;
    private final PartyInvitationService invitationService;

    @KafkaListener(topics = "${rpgym.kafka.notification-events-topic}")
    public void consume(String message) {
        NotificationEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message, NotificationEventEnvelope.class);
        } catch (JsonProcessingException e) {
            // 재시도해도 같은 문자열이 같은 곳에서 깨진다.
            log.error("notification event 역직렬화 실패. 건너뛴다. message={}", message, e);
            return;
        }

        if (envelope.eventType() == null || envelope.userId() == null) {
            log.error("envelope 필수 필드 누락. 건너뛴다. eventType={} userId={}",
                    envelope.eventType(), envelope.userId());
            return;
        }

        MDC.put("eventId", String.valueOf(envelope.eventId()));
        MDC.put("userId", String.valueOf(envelope.userId()));
        try {
            dispatch(envelope);
        } finally {
            MDC.remove("eventId");
            MDC.remove("userId");
        }
    }

    private void dispatch(NotificationEventEnvelope envelope) {
        if (PARTY_INVITATION_RESPONDED.equals(envelope.eventType())) {
            respond(envelope);
            return;
        }
        // quest 쪽 응답도 같은 토픽으로 온다. 파티가 모르는 타입은 조용히 ack 한다.
        log.debug("파티가 다루지 않는 eventType={}", envelope.eventType());
    }

    private void respond(NotificationEventEnvelope envelope) {
        PartyInvitationRespondedData data = convert(envelope.data());
        if (data == null) {
            return;
        }
        if (data.invitationId() == null || data.outcome() == null) {
            log.error("PARTY_INVITATION_RESPONDED 필수 필드 누락. 건너뛴다. data={}", envelope.data());
            return;
        }

        UUID userId = envelope.userId();
        UUID invitationId = data.invitationId();

        try {
            switch (data.outcome()) {
                case ACCEPTED -> accept(userId, invitationId);
                case REJECTED -> {
                    invitationService.reject(userId, invitationId);
                    log.debug("초대 거절 처리 완료. invitationId={}", invitationId);
                }
                default -> log.error("알 수 없는 outcome={} invitationId={}", data.outcome(), invitationId);
            }
        } catch (PartyException e) {
            // 도메인이 거부한 것은 재시도해도 같다. 유저에게 보여줄 결과는 이미 CLOSED 이벤트로 나갔거나
            // (만료 · 마감) 애초에 이 사람 것이 아닌 초대다.
            log.warn("초대 응답 거부. invitationId={} userId={} outcome={} code={}",
                    invitationId, userId, data.outcome(), e.getErrorCode().getCode());
        }
    }

    private void accept(UUID userId, UUID invitationId) {
        AcceptOutcome outcome = invitationService.accept(userId, invitationId);
        if (outcome instanceof AcceptOutcome.Full) {
            // 실패지만 정상 처리다. 초대는 CANCELED 로 닫혔고 PARTY_FULL CLOSED 가 슬랙으로 간다.
            log.info("초대 수락 실패 — 정원 초과. invitationId={} userId={}", invitationId, userId);
            return;
        }
        log.debug("초대 수락 처리 완료. invitationId={}", invitationId);
    }

    private PartyInvitationRespondedData convert(JsonNode data) {
        if (data == null || data.isNull()) {
            log.error("PARTY_INVITATION_RESPONDED data 가 비어 있다. 건너뛴다.");
            return null;
        }
        try {
            return objectMapper.treeToValue(data, PartyInvitationRespondedData.class);
        } catch (JsonProcessingException e) {
            log.error("PARTY_INVITATION_RESPONDED data 변환 실패. 건너뛴다. data={}", data, e);
            return null;
        }
    }
}
