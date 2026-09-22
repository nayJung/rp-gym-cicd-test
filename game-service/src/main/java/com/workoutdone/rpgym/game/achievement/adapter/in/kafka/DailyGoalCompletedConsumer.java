package com.workoutdone.rpgym.game.achievement.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.game.achievement.adapter.in.kafka.dto.DailyGoalCompletedData;
import com.workoutdone.rpgym.game.achievement.adapter.in.kafka.dto.HealthEventEnvelope;
import com.workoutdone.rpgym.game.achievement.application.AchievementProgressService;
import com.workoutdone.rpgym.game.achievement.domain.aggregate.Achievement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * health.events 에서 DAILY_GOAL_COMPLETED 만 골라 업적에 넘긴다.
 *
 * quest 의 HealthEventConsumer 와 같은 토픽을 읽지만 **컨슈머 그룹이 다르다** (game-service-achievement).
 * Kafka 는 그룹마다 전체 메시지를 독립적으로 전달하므로, 이 리스너와 quest 리스너는 서로의 offset 도
 * 코드도 모른다. quest 쪽은 DAILY_GOAL_COMPLETED 를 계속 무시하면 되고, 이쪽은 나머지 2종을 무시한다.
 * 이렇게 두는 이유는 하나다 -- quest 파일을 건드리지 않기 위해서. 파일이 다르니 PR 충돌이 없다.
 *
 * 대가: 같은 토픽을 두 번 읽는다. Health 이벤트는 유저당 30분에 1건이라 무시해도 되는 양이다.
 *
 * 그룹 ID 는 별도 프로퍼티(rpgym.kafka.achievement-consumer-group) 로 빼되 기본값을 여기 둔다.
 * application.yml 을 안 건드려도 동작하고, 필요하면 프로퍼티로 덮어쓴다.
 *
 * 예외 정책은 quest 컨슈머와 같다. 계약 위반(필드 누락 · 깨진 JSON)은 로그만 남기고 ack --
 * 몇 번을 다시 해도 같은 결과라 재시도가 의미 없고, 던지면 그 파티션이 영원히 막힌다.
 * DB 다운 · 낙관적 락 충돌은 서비스에서 올라오는 대로 던져 offset 을 잡지 않는다 (KafkaConsumerConfig 가 무한 재시도).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyGoalCompletedConsumer {

    static final String DAILY_GOAL_COMPLETED = "DAILY_GOAL_COMPLETED";

    private final ObjectMapper objectMapper;
    private final AchievementProgressService achievementProgressService;

    @KafkaListener(
            topics = "${rpgym.kafka.health-events-topic}",
            groupId = "${rpgym.kafka.achievement-consumer-group:game-service-achievement}"
    )
    public void consume(String message) {
        HealthEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message, HealthEventEnvelope.class);
        } catch (JsonProcessingException e) {
            log.error("health event 역직렬화 실패. 건너뛴다. message={}", message, e);
            return;
        }

        // 이 리스너의 관심사는 하나뿐이다. 나머지는 quest 컨슈머 몫이라 debug 도 남기지 않는다.
        if (!DAILY_GOAL_COMPLETED.equals(envelope.eventType())) {
            return;
        }
        if (envelope.userId() == null) {
            log.error("DAILY_GOAL_COMPLETED envelope 에 userId 가 없다. 건너뛴다. eventId={}", envelope.eventId());
            return;
        }

        MDC.put("eventId", String.valueOf(envelope.eventId()));
        MDC.put("userId", String.valueOf(envelope.userId()));
        try {
            record(envelope);
        } finally {
            MDC.remove("eventId");
            MDC.remove("userId");
        }
    }

    private void record(HealthEventEnvelope envelope) {
        if (envelope.data() == null || envelope.data().isNull()) {
            log.error("DAILY_GOAL_COMPLETED data 가 비어 있다. 건너뛴다.");
            return;
        }

        DailyGoalCompletedData data;
        try {
            data = objectMapper.treeToValue(envelope.data(), DailyGoalCompletedData.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("DAILY_GOAL_COMPLETED data 변환 실패. 건너뛴다. data={}", envelope.data(), e);
            return;
        }

        // activityDate 는 판정 키, achievedAt 은 user_achievements.achieved_at 에 그대로 남는 값. 둘 다 없으면 계약 위반.
        if (data.activityDate() == null || data.achievedAt() == null) {
            log.error("DAILY_GOAL_COMPLETED 필수 필드 누락. 건너뛴다. data={}", envelope.data());
            return;
        }

        List<Achievement> unlocked = achievementProgressService.recordDailyGoal(
                envelope.userId(), data.activityDate(), data.achievedAt().toInstant());
        log.debug("DAILY_GOAL_COMPLETED 처리 완료. activityDate={} unlocked={}",
                data.activityDate(), unlocked.stream().map(Achievement::getCode).toList());
    }
}
