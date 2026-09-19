package com.workoutdone.rpgym.health.summary.adapter.in;

import com.workoutdone.rpgym.health.summary.application.DeficientGoalDetectedEvent;
import com.workoutdone.rpgym.health.summary.application.QuestSuggestionAiPort;
import com.workoutdone.rpgym.health.summary.application.QuestSuggestionRecorder;
import com.workoutdone.rpgym.health.summary.application.event.QuestSuggestedPayload;
import com.workoutdone.rpgym.health.summary.domain.MetricType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestSuggestionEventListener {

    private static final String FALLBACK_STEPS_TITLE = "1,500보 더 걷기";
    private static final int FALLBACK_STEPS_TARGET = 1500;
    private static final String FALLBACK_ACTIVE_MINUTES_TITLE = "20분 산책하기";
    private static final int FALLBACK_ACTIVE_MINUTES_TARGET = 20;
    private static final String FALLBACK_ACTIVE_CALORIES_TITLE = "100kcal 운동하기";
    private static final int FALLBACK_ACTIVE_CALORIES_TARGET = 100;

    private final QuestSuggestionAiPort aiPort;
    private final RetryTemplate retryTemplate;
    private final QuestSuggestionRecorder questSuggestionRecorder;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DeficientGoalDetectedEvent event) {
        MetricType metricType = MetricType.valueOf(event.mostDeficientMetric());
        int targetValue = resolveTargetValue(metricType);

        String title;
        try {
            title = retryTemplate.execute(context ->
                    aiPort.generateTitle(metricType, event.shortageValue())
            );
        } catch (Exception e) {
            log.warn("AI Quest 제안 생성 실패, fallback 템플릿 사용. userId={}, metric={}",
                    event.userId(), metricType, e);
            title = resolveFallbackTitle(metricType);
        }

        UUID eventId = UUID.randomUUID();
        UUID suggestionId = UUID.randomUUID();
        String dedupKey = "QUEST_SUGGESTED:%s:%s".formatted(event.userId(), event.measuredAt());

        QuestSuggestedPayload payload = new QuestSuggestedPayload(
                suggestionId,
                event.activityDate(),
                event.measuredAt(),
                title,
                metricType.name(),
                targetValue
        );

        questSuggestionRecorder.record(eventId, event.userId(), event.activityId(), dedupKey, payload);
    }

    private int resolveTargetValue(MetricType metricType) {
        return switch (metricType) {
            case STEPS -> FALLBACK_STEPS_TARGET;
            case ACTIVE_MINUTES -> FALLBACK_ACTIVE_MINUTES_TARGET;
            case ACTIVE_CALORIES -> FALLBACK_ACTIVE_CALORIES_TARGET;
        };
    }

    private String resolveFallbackTitle(MetricType metricType) {
        return switch (metricType) {
            case STEPS -> FALLBACK_STEPS_TITLE;
            case ACTIVE_MINUTES -> FALLBACK_ACTIVE_MINUTES_TITLE;
            case ACTIVE_CALORIES -> FALLBACK_ACTIVE_CALORIES_TITLE;
        };
    }
}