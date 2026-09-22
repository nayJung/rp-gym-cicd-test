package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.outbox.application.OutboxRecorder;
import com.workoutdone.rpgym.game.outbox.domain.AggregateType;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;
import com.workoutdone.rpgym.game.quest.application.payload.QuestCompletedData;
import com.workoutdone.rpgym.game.quest.domain.aggregate.Quest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;
import com.workoutdone.rpgym.game.quest.domain.repo.QuestRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.UserLatestSnapshotRepository;
import com.workoutdone.rpgym.game.quest.domain.vo.ApplyResult;
import com.workoutdone.rpgym.game.quest.domain.vo.Snapshot;
import com.workoutdone.rpgym.game.xp.application.XpGrantService;
import com.workoutdone.rpgym.game.xp.domain.SourceType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestProgressService {

    private final QuestRepository questRepository;
    private final UserLatestSnapshotRepository userLatestSnapshotRepository;
    private final XpGrantService xpGrantService;
    private final OutboxRecorder outboxRecorder;

    // inbound(Driving) HEALTH_ACTIVITY_SYNCED 2 (T2)
    // 함께 커밋 되거나 삭제되는 Inbound
    // user_latest_snapshots, quests, xp_ledgers, wallets, outbox_events
    // 4개의 인스턴스가 함께 커밋 되거나 삭제(롤백)된다.
    @Transactional
    public Optional<ApplyResult> apply(UUID userId, Snapshot snapshot) {
        // inbound(Driving) HEALTH_ACTIVITY_SYNCED 3
        // 활성 Quest 조회보다 먼저 저장해서 Quest가 없어도 누적값을 저장한다.
        storeLatest(userId, snapshot);

        // inbound(Driving) HEALTH_ACTIVITY_SYNCED 4
        // 어댑터를 통한 0층 방어선
        // 'ACTIVE','expired_at'을 걸름
        // 완료된 퀘스트 인스턴스는 애초에 메모리에 올라오지도 않음
        // 영속 상태 객체(영속성 컨텍스트)
        // 같은 이벤트를 언제 처리하든 결과가 같아야함(멱등)-> Instant.now()를 안쓴이유
        Optional<Quest> active = questRepository.findActiveByUserId(userId, snapshot.measuredAt());
        if (active.isEmpty()) {
            return Optional.empty();
        }

        Quest quest = active.get();
        // inbound(Driving) HEALTH_ACTIVITY_SYNCED 5
        // 필드가 바뀌면서 커밋 시점에 Hibernate가 변경 감지로 update 쿼리를 날림
        // 워터마크와 누적값은 이미 DB에 적재됨
        ApplyResult result = quest.applySnapshot(snapshot);
        // .save()를 남긴 이유
        // 1. 포트를 통해서만 저장을 진행(핵사고날적 관점)
        // 2. 변경 감지는 영속상태의 객체일때만 작동함
        // 만약 준영속(detached)가 되면 에러없이 저장이 안되는 불상사 일어남
        // 그리고 추가쿼리도 나가지않음
        questRepository.save(quest);

        if (result instanceof ApplyResult.Completed completed) {
            xpGrantService.grant(
                    userId, SourceType.QUEST, quest.getQuestId(), quest.getRewardXp(), snapshot.measuredAt());
            // pgSQL과 Kafka는 서로 다른 시스템
            // 하나의 트랜잭션으로 묶을수는 없음
            // 반드시 둘중 하나가 먼저 일어나야함
            // 카프카는 롤백이 없으므로 DB 커밋을 먼저 진행해야함
            outboxRecorder.append(
                    AggregateType.QUEST,
                    quest.getQuestId(),
                    OutboxEventType.QUEST_COMPLETED,
                    userId,
                    // EnventEnvelope.occuredAt() -> 스냅샷을 잰 시각
                    snapshot.measuredAt(),
                    QuestCompletedData.from(quest, completed.achievedDelta(), snapshot.measuredAt())
            );

            log.info("quest completed: questId={} userId={} achievedDelta={} rewardXp={} measuredAt={}",
                    quest.getQuestId(), userId, completed.achievedDelta(),
                    quest.getRewardXp(), snapshot.measuredAt());
        } else {
            log.debug("snapshot applied: questId={} userId={} measuredAt={} result={}",
                    quest.getQuestId(), userId, snapshot.measuredAt(), result);
        }

        return Optional.of(result);
    }

    private void storeLatest(UUID userId, Snapshot snapshot) {
        Optional<UserLatestSnapshot> stored = userLatestSnapshotRepository.findByUserId(userId);

        if (stored.isEmpty()) {
            userLatestSnapshotRepository.save(UserLatestSnapshot.create(userId, snapshot));
            return;
        }

        UserLatestSnapshot latest = stored.get();
        if (latest.applyIfNewer(snapshot)) {
            userLatestSnapshotRepository.save(latest);
        }
    }
}
