package com.workoutdone.rpgym.health.summary.adapter.in;

import com.workoutdone.rpgym.health.summary.application.DeficientGoalDetectedEvent;
import com.workoutdone.rpgym.health.summary.application.QuestSuggestionAiPort;
import com.workoutdone.rpgym.health.summary.application.QuestSuggestionRecorder;
import com.workoutdone.rpgym.health.summary.application.event.QuestSuggestedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestSuggestionEventListenerTest {

    @Mock
    QuestSuggestionAiPort aiPort;
    @Mock
    QuestSuggestionRecorder questSuggestionRecorder;

    /** 실제 RetryTemplate을 그대로 써서, 재시도 흐름까지 진짜 동작을 검증한다 */
    private final RetryTemplate retryTemplate = new RetryTemplate();

    private QuestSuggestionEventListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final Instant measuredAt = Instant.parse("2026-08-30T01:00:00Z");

    @BeforeEach
    void setUp() {
        listener = new QuestSuggestionEventListener(aiPort, retryTemplate, questSuggestionRecorder);
    }

    @Test
    void AI가_정상_응답하면_그_제목으로_기록한다() {
        given(aiPort.generateTitle(any(), anyInt())).willReturn("AI가 만든 제목");

        DeficientGoalDetectedEvent event = new DeficientGoalDetectedEvent(
                userId, UUID.randomUUID(), activityId,
                LocalDate.of(2026, 8, 30), measuredAt, "STEPS", 4000
        );

        listener.handle(event);

        ArgumentCaptor<QuestSuggestedPayload> payloadCaptor = ArgumentCaptor.forClass(QuestSuggestedPayload.class);
        verify(questSuggestionRecorder).record(any(), eq(userId), eq(activityId), any(), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue().title()).isEqualTo("AI가 만든 제목");
        assertThat(payloadCaptor.getValue().metric()).isEqualTo("STEPS");
    }

    @Test
    void AI_호출이_계속_실패하면_fallback_제목으로_기록한다() {
        given(aiPort.generateTitle(any(), anyInt())).willThrow(new RuntimeException("Gemini 타임아웃"));

        DeficientGoalDetectedEvent event = new DeficientGoalDetectedEvent(
                userId, UUID.randomUUID(), activityId,
                LocalDate.of(2026, 8, 30), measuredAt, "STEPS", 4000
        );

        listener.handle(event);

        ArgumentCaptor<QuestSuggestedPayload> payloadCaptor = ArgumentCaptor.forClass(QuestSuggestedPayload.class);
        verify(questSuggestionRecorder).record(any(), eq(userId), eq(activityId), any(), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue().title()).isEqualTo("1,500보 더 걷기");
        assertThat(payloadCaptor.getValue().targetValue()).isEqualTo(1500);
    }
}