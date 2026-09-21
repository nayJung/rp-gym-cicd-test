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

/**
 * DeficientGoalDetectedEvent를 받아 AI Quest 제안을 생성하고 Outbox에 기록한다.
 *
 * AFTER_COMMIT + @Async로 처리한다.
 * sync() 트랜잭션이 커밋된 "이후에", 별도 스레드에서 실행되므로
 * Gemini 호출(최대 14.5초)이 sync() 응답 시간에 영향을 주지 않는다.
 *
 * ⚠️ 트레이드오프: sync() 커밋과 이 메서드의 실행(및 outbox 기록) 사이에는
 * 시간 간격이 생긴다. 그 사이에 인스턴스가 종료되면(배포, 장애 등)
 * summary.questSuggestedAt은 이미 갱신됐지만 QUEST_SUGGESTED 이벤트는
 * 유실될 수 있다. 이 경우 해당 사용자는 다음 30분 주기까지 제안을
 * 받지 못한다. #97로 제안 정책이 "30분마다 반복"으로 바뀌었기 때문에
 * 이 정도 유실 위험은 감수할 수 있다고 판단했다 (PR #116 리뷰 논의).
 */
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

        // eventId: outbox/Kafka 계층의 멱등 처리용 식별자
        // suggestionId: 도메인 상 Quest 제안 자체의 식별자 (Game Service가 이 값으로 Quest를 식별)
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