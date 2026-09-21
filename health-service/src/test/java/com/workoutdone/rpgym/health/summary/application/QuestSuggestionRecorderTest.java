package com.workoutdone.rpgym.health.summary.application;

import com.workoutdone.rpgym.health.outbox.application.EventOutboxPort;
import com.workoutdone.rpgym.health.outbox.domain.HealthEventType;
import com.workoutdone.rpgym.health.summary.application.event.QuestSuggestedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestSuggestionRecorderTest {

    @Mock
    EventOutboxPort eventOutboxPort;
    @InjectMocks
    QuestSuggestionRecorder recorder;

    @Test
    void QUEST_SUGGESTED_타입으로_outbox에_기록한다() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        String dedupKey = "QUEST_SUGGESTED:test";
        QuestSuggestedPayload payload = new QuestSuggestedPayload(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30),
                Instant.parse("2026-08-30T01:00:00Z"), "제목", "STEPS", 1500
        );

        recorder.record(eventId, userId, activityId, dedupKey, payload);

        verify(eventOutboxPort).append(eventId, HealthEventType.QUEST_SUGGESTED, userId, activityId, dedupKey, payload);
    }
}