package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.outbox.application.OutboxRecorder;
import com.workoutdone.rpgym.game.outbox.domain.AggregateType;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;
import com.workoutdone.rpgym.game.quest.application.payload.QuestCreatedData;
import com.workoutdone.rpgym.game.quest.domain.aggregate.Quest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.QuestSuggestion;
import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;
import com.workoutdone.rpgym.game.quest.domain.repo.QuestRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.QuestSuggestionRepository;
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

// 유저가 Slack 카드에서 수락이나 거절을 눌렀을 때 실행된다.
//
// 저장하는 쪽과 클래스를 나눈 이유는 세 가지가 서로 다르기 때문이다.
// 들어오는 문이 다르고(카프카 대 HTTP), 실패를 알리는 방법이 다르고(값 대 상태 코드),
// 필요한 의존이 다르다. 저장은 제안 저장소와 아웃박스만 있으면 되지만
// 수락은 퀘스트 저장소와 스냅샷 저장소와 보상 정책까지 필요하다.
// 한 클래스에 합치면 카드 한 장 띄우려고 주입한 빈에 퀘스트 생성 메서드가 함께 딸려 온다.
//
// 이 서비스에서 가장 중요한 한 줄은 근거 스냅샷 대조다. 아래 accept 안에 설명을 붙여뒀다.
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestSuggestionAcceptService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final QuestSuggestionRepository questSuggestionRepository;
    private final QuestRepository questRepository;
    private final UserLatestSnapshotRepository userLatestSnapshotRepository;
    private final RewardPolicy rewardPolicy;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public SuggestionDecision accept(UUID suggestionId, UUID userId) {
        // 여기서 서버 시계를 쓰는 것은 의도한 것이다.
        // 이벤트를 처리할 때는 같은 이벤트를 언제 처리하든 결과가 같아야 해서 측정 시각을 쓰지만,
        // 이것은 사람이 지금 버튼을 누른 순간을 판정하는 것이라 기준 시각의 성격 자체가 다르다.
        // 조회 API 가 지금 무엇이 보이는가를 물을 때 서버 시계를 쓰는 것과 같은 이유다.
        Instant now = Instant.now();

        Optional<QuestSuggestion> found = questSuggestionRepository.findById(suggestionId);
        Optional<SuggestionDecision.Reason> blocked = notDecidable(found, userId, now);
        if (blocked.isPresent()) {
            return failed(blocked.get(), suggestionId, userId);
        }
        QuestSuggestion suggestion = found.get();

        // 이미 진행 중인 퀘스트가 있으면 막되 제안은 대기 상태로 남긴다.
        // 지금 퀘스트를 끝내고 나서 다시 수락할 수 있어야 하기 때문이다.
        // 여기서 제안을 거절 상태로 바꾸면 유저가 고를 기회를 잃는다.
        //
        // 이 검사를 저장 시점에 두지 않은 이유가 여기 있다.
        // 저장하는 순간에 답해봐야 그 답은 30분 뒤 수락하는 순간에는 낡은 답이다.
        if (questRepository.findActiveByUserId(userId, now).isPresent()) {
            return failed(SuggestionDecision.Reason.QUEST_ALREADY_ACTIVE, suggestionId, userId);
        }

        Optional<UserLatestSnapshot> stored = userLatestSnapshotRepository.findByUserId(userId);
        if (stored.isEmpty()) {
            return failed(SuggestionDecision.Reason.SNAPSHOT_MISSING, suggestionId, userId);
        }
        Snapshot baseline = stored.get().toSnapshot();

        // 이 서비스의 핵심이다.
        //
        // 제안이 만들어진 시점과 유저가 수락하는 시점 사이에는 몇십 분이 비어 있다.
        // 그 사이에 건강 데이터 동기화가 한 번 끼어들면 이런 일이 생긴다.
        //
        //   09:30  동기화 + 제안 도착 (근거는 3000보 시점)
        //   10:00  동기화만 들어옴. 저장된 최신 스냅샷은 이제 8000보 시점
        //   10:05  유저가 수락
        //          3000 을 baseline 으로 잡으면 그 사이에 걸은 5000보가 소급 인정된다
        //          목표가 1800보였으면 수락하는 순간 이미 달성이다
        //
        // 수락하는 시점에 baseline 을 새로 잡는 방법도 검토했는데 과했다.
        // 동기화 주기가 30분이라 대부분의 경우 새로 잡아도 같은 값이 나온다.
        // 깨지는 것은 위처럼 그 사이에 동기화가 끼어드는 경우뿐이다.
        //
        // 그래서 baseline 은 제안이 근거한 것을 그대로 쓰되, 그것이 여전히 최신인지만 대조한다.
        // 한 문장으로 하면, 제안은 그것이 근거한 스냅샷이 여전히 최신인 동안만 유효하다.
        //
        // 저장된 스냅샷이 제안의 근거보다 더 새것이든 더 옛것이든 똑같이 무효로 본다.
        // 옛것이라면 근거가 된 동기화가 아직 도착하지 않았다는 뜻이고 그것도 정상이 아니다.
        if (!baseline.measuredAt().equals(suggestion.getBasedOnMeasuredAt())) {
            // 이 상태 변경이 반드시 커밋되어야 해서 여기서 예외를 던지지 않는다.
            // 예외로 409 를 만들면 트랜잭션이 롤백되면서 이 변경이 사라지고,
            // 다음 수락 시도에서 또 같은 판정을 받고 또 롤백된다. 영원히 끝나지 않는다.
            suggestion.supersede(now);
            questSuggestionRepository.save(suggestion);
            return failed(SuggestionDecision.Reason.SUPERSEDED, suggestionId, userId);
        }

        // 측정 시각은 같은데 활동 날짜가 다른 경우다. 자정 경계에서 생긴다.
        // 측정은 어제 23시 59분인데 제안이 0시 5분에 발행되어 활동 날짜만 오늘로 찍히면 이렇게 된다.
        // 그대로 통과시키면 baseline 이 어제 누적값이 되는데 오늘 누적값은 0부터 시작하므로
        // 달성분이 계속 음수가 되어 영원히 완료되지 않는다.
        //
        // 위와 같이 무효로 만든다. 다시 시도해도 결과가 달라지지 않는 제안을
        // 대기 상태로 남겨두면 유저가 30분 동안 계속 눌러보게 된다.
        if (!baseline.activityDate().equals(suggestion.getActivityDate())) {
            suggestion.supersede(now);
            questSuggestionRepository.save(suggestion);
            return failed(SuggestionDecision.Reason.DATE_MISMATCH, suggestionId, userId);
        }

        Quest quest = questRepository.save(Quest.create(
                UUID.randomUUID(),
                userId,
                suggestion.getSuggestionId(),
                suggestion.getTitle(),
                suggestion.getMetric(),
                suggestion.getTargetVal(),
                // 대조를 통과했다는 것은 저장된 이 스냅샷이 제안이 근거한 바로 그 스냅샷이라는 뜻이다.
                // 그래서 여기서 누적값을 꺼내 쓸 수 있다.
                // 제안 행에 누적값을 따로 보관하지 않은 이유이기도 하다.
                // Health 가 보내는 제안 이벤트에는 애초에 누적값이 실려 있지 않다.
                baseline.valueOf(suggestion.getMetric()),
                baseline.measuredAt(),
                // 보상 금액은 이벤트에서 오지 않는다. Health 도 AI 도 정하지 못한다.
                rewardPolicy.questRewardXp(),
                endOfDay(suggestion.getActivityDate())
        ));

        suggestion.accept(quest.getQuestId(), now);
        questSuggestionRepository.save(suggestion);

        outboxRecorder.append(
                AggregateType.QUEST,
                quest.getQuestId(),
                OutboxEventType.QUEST_CREATED,
                userId,
                quest.getBaselineMeasuredAt(),
                QuestCreatedData.from(quest)
        );

        log.info("quest suggestion accepted: suggestionId={} questId={} userId={} baseline={}",
                suggestionId, quest.getQuestId(), userId, quest.getBaselineVal());

        return new SuggestionDecision.Accepted(QuestView.from(quest));
    }

    @Transactional
    public SuggestionDecision reject(UUID suggestionId, UUID userId) {
        Instant now = Instant.now();

        Optional<QuestSuggestion> found = questSuggestionRepository.findById(suggestionId);
        Optional<SuggestionDecision.Reason> blocked = notDecidable(found, userId, now);
        if (blocked.isPresent()) {
            return failed(blocked.get(), suggestionId, userId);
        }

        // 거절은 퀘스트를 만들지 않고 이벤트도 내보내지 않는다. 제안을 닫기만 한다.
        // 무시와 거절을 굳이 구분하는 이유는 나중에 제안 수락률을 볼 때
        // 안 본 것과 보고 싫다고 한 것이 다른 정보이기 때문이다.
        QuestSuggestion suggestion = found.get();
        suggestion.reject(now);
        questSuggestionRepository.save(suggestion);

        log.info("quest suggestion rejected by user: suggestionId={} userId={}", suggestionId, userId);

        return new SuggestionDecision.Rejected(suggestionId);
    }

    // 수락과 거절이 공통으로 통과해야 하는 세 가지다.
    // 비어 있으면 통과고, 값이 있으면 그것이 막힌 이유다.
    private Optional<SuggestionDecision.Reason> notDecidable(
            Optional<QuestSuggestion> found, UUID userId, Instant now) {

        // 없는 제안과 남의 제안을 같은 결과로 묶는다.
        // 구분해서 알려주면 식별자를 하나씩 넣어보는 것만으로 남의 제안이 존재하는지 알 수 있다.
        if (found.isEmpty() || !found.get().getUserId().equals(userId)) {
            return Optional.of(SuggestionDecision.Reason.NOT_FOUND);
        }
        QuestSuggestion suggestion = found.get();

        // 만료보다 먼저 본다. 이미 수락한 제안이 시간이 지나 만료까지 됐을 때
        // 만료됐다고 답하는 것보다 이미 결정했다고 답하는 쪽이 유저에게 정확하다.
        if (!suggestion.isPending()) {
            return Optional.of(SuggestionDecision.Reason.ALREADY_DECIDED);
        }

        // 만료된 제안의 상태를 배치로 바꾸지 않는다.
        // 만료는 저장된 시각으로 계산되는 값이고, 계산할 수 있는 것을 저장하면
        // 그 저장을 누가 언제 갱신하느냐는 문제가 생긴다. 그리고 갱신은 항상 늦는다.
        // 배치가 도는 사이의 틈에서 이미 만료된 제안이 대기 상태로 보이면 그대로 수락되어 버린다.
        if (suggestion.isExpired(now)) {
            return Optional.of(SuggestionDecision.Reason.ALREADY_EXPIRED);
        }

        return Optional.empty();
    }

    // 퀘스트의 만료 시각은 활동 날짜의 한국 시간 자정 직전이다.
    // Quest 를 만드는 쪽에서는 만료가 baseline 측정 시각보다 뒤인지만 보고
    // 같은 날 안인지는 보지 않는다. 그 조건을 지키는 책임이 여기 있다.
    private static Instant endOfDay(LocalDate activityDate) {
        return activityDate.atTime(END_OF_DAY).atZone(KST).toInstant();
    }

    private SuggestionDecision failed(SuggestionDecision.Reason reason, UUID suggestionId, UUID userId) {
        // 스냅샷이 없다는 것만 계약 위반이라 에러로 남긴다.
        // 나머지는 유저가 늦게 눌렀거나 이미 눌렀다는 뜻이라 정상 동작이다.
        if (reason == SuggestionDecision.Reason.SNAPSHOT_MISSING) {
            log.error("quest suggestion decision failed: reason={} suggestionId={} userId={}",
                    reason, suggestionId, userId);
        } else {
            log.info("quest suggestion decision failed: reason={} suggestionId={} userId={}",
                    reason, suggestionId, userId);
        }
        return new SuggestionDecision.Failed(reason);
    }
}
