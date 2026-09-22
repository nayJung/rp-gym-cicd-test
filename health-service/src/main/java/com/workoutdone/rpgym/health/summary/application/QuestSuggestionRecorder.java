package com.workoutdone.rpgym.health.summary.application;

import com.workoutdone.rpgym.health.outbox.application.EventOutboxPort;
import com.workoutdone.rpgym.health.outbox.domain.HealthEventType;
import com.workoutdone.rpgym.health.summary.application.event.QuestSuggestedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Component
@RequiredArgsConstructor
public class QuestSuggestionRecorder {

    private final EventOutboxPort eventOutboxPort;

    @Transactional
    public void record(UUID eventId, UUID userId, UUID activityId, String dedupKey,
                       QuestSuggestedPayload payload) {
        eventOutboxPort.append(
                eventId,
                HealthEventType.QUEST_SUGGESTED,
                userId,
                activityId,
                dedupKey,
                payload
        );
    }
}