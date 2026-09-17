package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.outbox.application.OutboxRecorder;
import com.workoutdone.rpgym.game.outbox.domain.AggregateType;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;
import com.workoutdone.rpgym.game.quest.application.payload.QuestCreatedData;
import com.workoutdone.rpgym.game.quest.domain.Metric;
import com.workoutdone.rpgym.game.quest.domain.aggregate.Quest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;
import com.workoutdone.rpgym.game.quest.domain.repo.QuestRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.UserLatestSnapshotRepository;
import com.workoutdone.rpgym.game.quest.domain.vo.Snapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestSuggestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final QuestRepository questRepository;
    private final UserLatestSnapshotRepository userLatestSnapshotRepository;
    private final RewardPolicy rewardPolicy;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    // T1 열림
    public SuggestionOutcome accept(QuestSuggestionCommand command) {
        // 같은 제안이 다시 전송되는 것은 at-least-once(카프카)에서 정상이다.
        // 상태와 무관하게 여기서 걸러야 한다 .
        // ALREADY_ACTIVE if문은
        // 퀘스트가 COMPLETED/EXPIRED가 된 뒤의 재송신을 통과시키고,
        // 그러면 uk_quests_suggestion 위반이 예외로 터져 파티션이 멈춘다.
        // 따라서 멱등성 방어에 대한 방어 코드를 애플리케이션 레이어에도 작성함으로써
        // DB 유니크(2차)가 DB layer가 수행되도록 1차 방어를 여기서 해야한다.
        // 가용성을 챙기기 위한 코드 한줄
        // 즉 " 이 이벤트를 미리 처리했나?" 를 묻는 사실확인 가드
        if (questRepository.existsBySuggestionId(command.suggestionId())) {
            return discarded(SuggestionOutcome.DUPLICATE_SUGGESTION, command);
        }

        // 49줄 코드가 없으면 DataIntegrityViolationException
        // -> 컨슈머 밖으로 던져짐
        // → FixedBackOff(2s, UNLIMITED_ATTEMPTS) 를 만남
        // → 2초 뒤 재시도 → 같은 문자열이 같은 제약에 걸림 → 또 실패
        //  → 그 파티션이 영원히 멈춘다
        // 가용성이 깨짐
        // "이 유저에게 하나 더 줘도 되나"를 묻는 정책판단 가드
        // 또 Instant.now()는 기준선과는 아무 상관없음
        // 기준선은 stored에서 꺼내옴
        // findActiveByUserId의 expired_at>now에 들어감
        if (questRepository.findActiveByUserId(command.userId(), Instant.now()).isPresent()) {
            return discarded(SuggestionOutcome.ALREADY_ACTIVE, command);
        }

        Optional<Metric> metric = Metric.from(command.metric());
        if (metric.isEmpty()) {
            return rejected(SuggestionOutcome.UNKNOWN_METRIC, command);
        }
        if (command.targetValue() <= 0) {
            return rejected(SuggestionOutcome.INVALID_TARGET, command);
        }

        Optional<UserLatestSnapshot> stored = userLatestSnapshotRepository.findByUserId(command.userId());
        if (stored.isEmpty()) {
            return rejected(SuggestionOutcome.SNAPSHOT_MISSING, command);
        }
        // baseLine 조달 -> 저장된 엔티티가 snapshot과 같은 5개 필드를 가짐
        // 스냅샷의 내부 함수가 3지표 중 하나를 꺼낸다.
        // UserLatestSnapshot을 객체화하는 코드
        // 참조 객체 -> 실제 객체 -> 현재 상태를 불변 객체로 변환
        Snapshot baseline = stored.get().toSnapshot();
        // 제안이 근거한 스냅샷이 measuredAt()이 맞는가?
        // basedOnMeasuredAt()은 시각이 아니라 포인터
        // 저장된 최신 스냅샷이 제안이 근거한 스냅샷과 다르면
        // 더 새것이든, 옛것이든 똑같이 잘못된 스냅샷임을 의미한다.
        if (!baseline.measuredAt().equals(command.basedOnMeasuredAt())) {
            return rejected(SuggestionOutcome.SNAPSHOT_MISMATCH, command);
        }
        if (!baseline.activityDate().equals(command.activityDate())) {
            return discarded(SuggestionOutcome.DATE_MISMATCH, command);
        }

        // T1 -> outbound Commit()
        Quest quest = questRepository.save(Quest.create(
                UUID.randomUUID(),
                command.userId(),
                command.suggestionId(),
                command.title(),
                metric.get(),
                command.targetValue(),
                baseline.valueOf(metric.get()),
                baseline.measuredAt(),
                rewardPolicy.questRewardXp(),
                endOfDay(command.activityDate())
        ));

        // T1 close
        outboxRecorder.append(
                AggregateType.QUEST,
                quest.getQuestId(),
                OutboxEventType.QUEST_CREATED,
                quest.getUserId(),
                // EnventEnvelope.occuredAt() -> 기준선을 잰 시각
                quest.getBaselineMeasuredAt(),
                QuestCreatedData.from(quest)
        );

        log.info("quest created: questId={} userId={} suggestionId={} metric={} target={} baseline={}",
                quest.getQuestId(), quest.getUserId(), quest.getSuggestionId(),
                quest.getMetric(), quest.getTargetVal(), quest.getBaselineVal());

        return SuggestionOutcome.CREATED;
    }

    private static Instant endOfDay(LocalDate activityDate) {
        return activityDate.atTime(END_OF_DAY).atZone(KST).toInstant();
    }

    private SuggestionOutcome discarded(SuggestionOutcome outcome, QuestSuggestionCommand command) {
        log.info("quest suggestion discarded: outcome={} suggestionId={} userId={}",
                outcome, command.suggestionId(), command.userId());
        return outcome;
    }

    private SuggestionOutcome rejected(SuggestionOutcome outcome, QuestSuggestionCommand command) {
        log.error("quest suggestion rejected: outcome={} suggestionId={} userId={} metric={} target={} basedOnMeasuredAt={}",
                outcome, command.suggestionId(), command.userId(),
                command.metric(), command.targetValue(), command.basedOnMeasuredAt());
        return outcome;
    }
}
