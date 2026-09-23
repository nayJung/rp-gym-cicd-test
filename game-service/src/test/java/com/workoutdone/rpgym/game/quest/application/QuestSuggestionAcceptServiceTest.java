package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.outbox.application.OutboxRecorder;
import com.workoutdone.rpgym.game.outbox.domain.AggregateType;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;
import com.workoutdone.rpgym.game.quest.domain.Metric;
import com.workoutdone.rpgym.game.quest.domain.QuestStatus;
import com.workoutdone.rpgym.game.quest.domain.SuggestionStatus;
import com.workoutdone.rpgym.game.quest.domain.aggregate.Quest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.QuestSuggestion;
import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;
import com.workoutdone.rpgym.game.quest.domain.repo.QuestRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.QuestSuggestionRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.UserLatestSnapshotRepository;
import com.workoutdone.rpgym.game.quest.domain.vo.Snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 유저가 Slack 카드에서 수락이나 거절을 눌렀을 때의 경로를 본다.
//
// 이 서비스는 지금 시각을 서버 시계로 읽는다. 사람이 버튼을 누른 순간을 판정하기 때문이다.
// 그래서 테스트도 고정된 과거 시각을 쓸 수 없고 지금 기준으로 제안을 만든다.
class QuestSuggestionAcceptServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID SUGGESTION_ID = UUID.randomUUID();

    // 1분 전에 측정된 스냅샷을 근거로 하는 제안이다. 만료까지 29분 남아 있다.
    private static final Instant BASED_ON = Instant.now().minus(Duration.ofMinutes(1));
    private static final LocalDate DATE = LocalDate.now(KST);

    private QuestSuggestionRepository suggestionRepository;
    private QuestRepository questRepository;
    private UserLatestSnapshotRepository snapshotRepository;
    private OutboxRecorder outboxRecorder;
    private QuestSuggestionAcceptService service;

    @BeforeEach
    void setUp() {
        suggestionRepository = mock(QuestSuggestionRepository.class);
        questRepository = mock(QuestRepository.class);
        snapshotRepository = mock(UserLatestSnapshotRepository.class);
        outboxRecorder = mock(OutboxRecorder.class);
        service = new QuestSuggestionAcceptService(
                suggestionRepository, questRepository, snapshotRepository,
                new RewardPolicy(), outboxRecorder);

        when(suggestionRepository.findById(SUGGESTION_ID)).thenReturn(Optional.of(pendingSuggestion()));
        when(suggestionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(questRepository.findActiveByUserId(any(), any())).thenReturn(Optional.empty());
        when(questRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(snapshotRepository.findByUserId(USER_ID)).thenReturn(Optional.of(storedSnapshot(BASED_ON, DATE)));
    }

    private QuestSuggestion pendingSuggestion() {
        return suggestionBasedOn(BASED_ON, DATE);
    }

    private QuestSuggestion suggestionBasedOn(Instant basedOn, LocalDate date) {
        return QuestSuggestion.create(
                SUGGESTION_ID, USER_ID, "20분 산책하기", Metric.ACTIVE_MINUTES, 20,
                date, basedOn, basedOn.plus(Duration.ofMinutes(30)));
    }

    private UserLatestSnapshot storedSnapshot(Instant measuredAt, LocalDate date) {
        return UserLatestSnapshot.create(USER_ID, new Snapshot(date, measuredAt, 3000, 31, 155));
    }

    private static SuggestionDecision.Reason reasonOf(SuggestionDecision decision) {
        return assertInstanceOf(SuggestionDecision.Failed.class, decision).reason();
    }

    @Test
    @DisplayName("수락하면 baseline이 저장된 스냅샷의 누적값으로 고정된다")
    void baseline은_저장된_스냅샷에서_온다() {
        ArgumentCaptor<Quest> saved = ArgumentCaptor.forClass(Quest.class);

        SuggestionDecision decision = service.accept(SUGGESTION_ID, USER_ID);

        assertInstanceOf(SuggestionDecision.Accepted.class, decision);
        verify(questRepository).save(saved.capture());
        Quest quest = saved.getValue();
        assertEquals(Metric.ACTIVE_MINUTES, quest.getMetric());
        // 스냅샷의 활동 분이 31이므로 baseline도 31이다. 제안 행에는 누적값이 없다.
        assertEquals(31, quest.getBaselineVal());
        assertEquals(BASED_ON, quest.getBaselineMeasuredAt());
        assertEquals(20, quest.getTargetVal());
        // 보상 금액은 이벤트에도 제안 행에도 없다. 정책이 정한다.
        assertEquals(5, quest.getRewardXp());
        assertEquals(QuestStatus.ACTIVE, quest.getStatus());
        assertEquals(SUGGESTION_ID, quest.getSuggestionId());
    }

    @Test
    @DisplayName("수락하면 제안이 닫히고 만들어진 퀘스트가 연결된다")
    void 수락하면_제안이_닫힌다() {
        ArgumentCaptor<QuestSuggestion> saved = ArgumentCaptor.forClass(QuestSuggestion.class);

        service.accept(SUGGESTION_ID, USER_ID);

        verify(suggestionRepository).save(saved.capture());
        assertEquals(SuggestionStatus.ACCEPTED, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getQuestId());
        assertNotNull(saved.getValue().getDecidedAt());
    }

    @Test
    @DisplayName("수락하면 QuestCreated가 아웃박스에 적재된다")
    void 수락하면_아웃박스에_적재된다() {
        service.accept(SUGGESTION_ID, USER_ID);

        verify(outboxRecorder).append(
                eq(AggregateType.QUEST),
                any(UUID.class),
                eq(OutboxEventType.QUEST_CREATED),
                eq(USER_ID),
                eq(BASED_ON),
                any());
    }

    @Test
    @DisplayName("없는 제안이면 못 찾았다고 답한다")
    void 없는_제안() {
        when(suggestionRepository.findById(SUGGESTION_ID)).thenReturn(Optional.empty());

        assertEquals(SuggestionDecision.Reason.NOT_FOUND,
                reasonOf(service.accept(SUGGESTION_ID, USER_ID)));
        verify(questRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 제안도 못 찾았다고 답한다 — 남의 것이라고 알려주면 존재 여부가 새어 나간다")
    void 남의_제안() {
        assertEquals(SuggestionDecision.Reason.NOT_FOUND,
                reasonOf(service.accept(SUGGESTION_ID, OTHER_USER_ID)));
        verify(questRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 결정된 제안은 다시 결정할 수 없다")
    void 이미_결정된_제안() {
        QuestSuggestion decided = pendingSuggestion();
        decided.reject(Instant.now());
        when(suggestionRepository.findById(SUGGESTION_ID)).thenReturn(Optional.of(decided));

        assertEquals(SuggestionDecision.Reason.ALREADY_DECIDED,
                reasonOf(service.accept(SUGGESTION_ID, USER_ID)));
        verify(questRepository, never()).save(any());
    }

    @Test
    @DisplayName("30분이 지난 제안은 수락할 수 없다 — 상태는 대기인 채로 남아 있다")
    void 만료된_제안() {
        // 40분 전 측정 기준이므로 만료 시각이 10분 전이다.
        // 이 제안의 상태 컬럼은 여전히 대기다. 만료를 상태로 저장하지 않고 시각으로 계산한다.
        Instant longAgo = Instant.now().minus(Duration.ofMinutes(40));
        QuestSuggestion expired = suggestionBasedOn(longAgo, DATE);
        when(suggestionRepository.findById(SUGGESTION_ID)).thenReturn(Optional.of(expired));

        assertEquals(SuggestionStatus.PENDING, expired.getStatus());
        assertEquals(SuggestionDecision.Reason.ALREADY_EXPIRED,
                reasonOf(service.accept(SUGGESTION_ID, USER_ID)));
        verify(questRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 진행 중인 퀘스트가 있으면 막되 제안은 대기 상태로 남긴다")
    void 이미_활성_퀘스트가_있으면_제안을_남긴다() {
        when(questRepository.findActiveByUserId(any(), any()))
                .thenReturn(Optional.of(mock(Quest.class)));

        assertEquals(SuggestionDecision.Reason.QUEST_ALREADY_ACTIVE,
                reasonOf(service.accept(SUGGESTION_ID, USER_ID)));

        // 여기서 제안을 거절로 만들면 유저가 지금 퀘스트를 끝내고 다시 수락할 기회를 잃는다.
        verify(suggestionRepository, never()).save(any());
        verify(questRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장된 스냅샷이 없으면 수락할 수 없다")
    void 스냅샷이_없으면_막는다() {
        when(snapshotRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertEquals(SuggestionDecision.Reason.SNAPSHOT_MISSING,
                reasonOf(service.accept(SUGGESTION_ID, USER_ID)));
        verify(questRepository, never()).save(any());
    }

    @Test
    @DisplayName("근거 스냅샷이 낡았으면 제안을 무효로 바꾸고 그 변경이 저장된다")
    void 근거가_낡으면_무효로_만든다() {
        // 제안이 도착한 뒤 동기화가 한 번 더 들어와서 저장된 스냅샷이 더 새것이 된 상황이다.
        // 이대로 수락시키면 그 사이에 걸은 만큼이 baseline 밖으로 밀려나 소급 인정된다.
        Instant newer = BASED_ON.plus(Duration.ofSeconds(30));
        when(snapshotRepository.findByUserId(USER_ID)).thenReturn(Optional.of(storedSnapshot(newer, DATE)));

        ArgumentCaptor<QuestSuggestion> saved = ArgumentCaptor.forClass(QuestSuggestion.class);

        assertEquals(SuggestionDecision.Reason.SUPERSEDED,
                reasonOf(service.accept(SUGGESTION_ID, USER_ID)));

        // 이 단정이 이 테스트의 핵심이다.
        // 실패를 예외로 던졌다면 트랜잭션이 롤백되면서 이 상태 변경이 통째로 사라지고,
        // 다음 수락 시도에서 또 같은 판정을 받고 또 롤백된다. 영원히 끝나지 않는다.
        verify(suggestionRepository).save(saved.capture());
        assertEquals(SuggestionStatus.SUPERSEDED, saved.getValue().getStatus());
        verify(questRepository, never()).save(any());
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    @DisplayName("활동 날짜가 어긋나면 제안을 무효로 바꾼다 — 다시 눌러도 결과가 같다")
    void 활동_날짜가_어긋나면_무효로_만든다() {
        // 측정 시각은 맞는데 날짜만 어긋난 경우다. 자정 경계에서 생긴다.
        when(snapshotRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(storedSnapshot(BASED_ON, DATE.minusDays(1))));

        ArgumentCaptor<QuestSuggestion> saved = ArgumentCaptor.forClass(QuestSuggestion.class);

        assertEquals(SuggestionDecision.Reason.DATE_MISMATCH,
                reasonOf(service.accept(SUGGESTION_ID, USER_ID)));

        // 대기 상태로 남겨두면 유저가 30분 동안 계속 눌러보게 된다. 결과는 매번 같은데도.
        verify(suggestionRepository).save(saved.capture());
        assertEquals(SuggestionStatus.SUPERSEDED, saved.getValue().getStatus());
        verify(questRepository, never()).save(any());
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    @DisplayName("거절하면 제안만 닫히고 퀘스트도 이벤트도 생기지 않는다")
    void 거절하면_제안만_닫힌다() {
        ArgumentCaptor<QuestSuggestion> saved = ArgumentCaptor.forClass(QuestSuggestion.class);

        SuggestionDecision decision = service.reject(SUGGESTION_ID, USER_ID);

        assertInstanceOf(SuggestionDecision.Rejected.class, decision);
        verify(suggestionRepository).save(saved.capture());
        assertEquals(SuggestionStatus.REJECTED, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getDecidedAt());
        verify(questRepository, never()).save(any());
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    @DisplayName("거절도 남의 제안이면 못 찾았다고 답한다")
    void 거절도_소유자만_할_수_있다() {
        assertEquals(SuggestionDecision.Reason.NOT_FOUND,
                reasonOf(service.reject(SUGGESTION_ID, OTHER_USER_ID)));
        verify(suggestionRepository, never()).save(any());
    }
}
