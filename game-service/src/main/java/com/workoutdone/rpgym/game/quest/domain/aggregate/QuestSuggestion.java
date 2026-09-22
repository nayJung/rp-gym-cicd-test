package com.workoutdone.rpgym.game.quest.domain.aggregate;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedEntity;
import com.workoutdone.rpgym.game.quest.domain.Metric;
import com.workoutdone.rpgym.game.quest.domain.SuggestionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// Health 가 보낸 퀘스트 제안을 유저가 수락할 때까지 보관하는 애그리거트.
//
// 이 테이블을 따로 둔 이유는 quests 테이블의 성질을 지키기 위해서다.
// 수락 대기 상태를 quests.status 에 PENDING 으로 얹으면
// "ACTIVE Quest 는 baseline 이 확정돼 있다", "유저당 동시 ACTIVE 는 하나" 같은
// 지금 quests 가 가진 조건들이 전부 무너지고, 모든 조회에 "단 PENDING 은 빼고" 가 붙는다.
// 판정 코드가 아직 수락되지 않은 행을 만날 수 있게 되는 것이 가장 큰 문제다.
//
// 테이블 하나를 더 만드는 대신 "quests 에 행이 있다 = 유저가 수락했다" 는 등식을 지킨다.
// 그 결과 Quest 애그리거트와 applySnapshot 판정 로직은 한 줄도 바뀌지 않는다.
@Entity
@Getter
@Table(name = "quest_suggestions", schema = "game_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestSuggestion extends BaseCreatedUpdatedEntity {

    // Health 가 자기 아웃박스에서 발급한 이벤트 식별자를 그대로 기본키로 쓴다.
    // 같은 제안이 두 번 배달되면 기본키 충돌로 걸린다.
    // 다만 그 충돌은 예외로 터지고 예외는 컨슈머에서 무한 재시도가 되므로,
    // 애플리케이션이 그 앞에서 조용히 먼저 답한다. 이 기본키는 두 번째 방어선이다.
    @Id
    @Column(name = "suggestion_id", nullable = false, updatable = false)
    private UUID suggestionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 20, updatable = false)
    private Metric metric;

    @Column(name = "target_val", nullable = false, updatable = false)
    private int targetVal;

    @Column(name = "activity_date", nullable = false, updatable = false)
    private LocalDate activityDate;

    // 이 제안이 근거로 삼은 건강 데이터 스냅샷의 측정 시각이다.
    //
    // 여기에 누적값 자체는 담지 않는다. Health 가 보내는 제안 이벤트에 누적값이 실려 있지 않기 때문이다.
    // 그래서 이 컬럼은 값이 아니라 포인터로 쓴다.
    // 수락할 때 내가 따로 보관 중인 유저별 최신 스냅샷을 읽어서, 그 스냅샷의 측정 시각이
    // 이 값과 같을 때만 그 스냅샷의 누적값을 baseline 으로 쓴다.
    // 두 값이 다르다는 것은 제안과 수락 사이에 동기화가 한 번 끼어들었다는 뜻이고,
    // 그때는 이 제안을 무효로 만든다.
    @Column(name = "based_on_measured_at", nullable = false, updatable = false)
    private Instant basedOnMeasuredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SuggestionStatus status;

    // 제안이 근거한 측정 시각으로부터 30분 뒤다.
    //
    // 서버 시계로 계산하지 않는다. 같은 이벤트를 언제 처리하든 같은 값이 나와야 하기 때문이다.
    // 컨슈머가 잠시 죽었다 20분 뒤에 살아나서 이 제안을 처리하면,
    // 서버 시계 기준이면 이미 낡은 제안이 30분을 새로 받아 살아난다.
    // 측정 시각 기준이면 유저에게 10분만 남는데, 제안의 근거가 30분 지나면 낡은 것이 맞으므로
    // 그쪽이 정확한 동작이다.
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    // 수락됐을 때만 채워진다. 어느 제안에서 어느 퀘스트가 나왔는지 역추적하는 용도다.
    @Column(name = "quest_id")
    private UUID questId;

    // 호출하기 전에 애플리케이션 계층이 확인해야 하는 것은 두 가지다.
    // 같은 제안이 이미 저장돼 있는지, 그리고 metric 문자열이 세 종류 안에 드는지.
    // 둘 다 데이터베이스를 봐야 알 수 있거나 문자열 파싱이 필요해서 여기서 하지 않는다.
    public static QuestSuggestion create(
            UUID suggestionId,
            UUID userId,
            String title,
            Metric metric,
            int targetVal,
            LocalDate activityDate,
            Instant basedOnMeasuredAt,
            Instant expiresAt
    ) {
        // 목표값이 0 이하이면 첫 스냅샷에서 달성분 0 이 목표 0 이상이 되어 즉시 완료된다.
        // 수락하자마자 공짜로 XP 가 나간다는 뜻이다.
        if (targetVal <= 0) {
            throw new IllegalArgumentException("targetVal must be positive but was " + targetVal);
        }
        // 제목이 없으면 유저가 무엇을 하라는 건지 알 수 없다. 카드에 그릴 내용이 없다.
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        // 태어날 때부터 만료된 제안은 어떤 수락 시도도 통과하지 못한다.
        if (!expiresAt.isAfter(basedOnMeasuredAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after basedOnMeasuredAt: " + expiresAt + " <= " + basedOnMeasuredAt);
        }

        QuestSuggestion suggestion = new QuestSuggestion();
        suggestion.suggestionId = suggestionId;
        suggestion.userId = userId;
        suggestion.title = title;
        suggestion.metric = metric;
        suggestion.targetVal = targetVal;
        suggestion.activityDate = activityDate;
        suggestion.basedOnMeasuredAt = basedOnMeasuredAt;
        suggestion.expiresAt = expiresAt;
        suggestion.status = SuggestionStatus.PENDING;
        suggestion.decidedAt = null;
        suggestion.questId = null;
        return suggestion;
    }

    public boolean isPending() {
        return status == SuggestionStatus.PENDING;
    }

    // 주어진 시각이 만료 시각과 같거나 그 뒤면 만료다.
    // 정확히 만료 시각인 순간을 살아 있는 것으로 치지 않는 이유는,
    // "30분 안에 수락하지 않으면 폐기한다" 는 규칙에서 30분이 지난 시점은 30분 안이 아니기 때문이다.
    public boolean isExpired(Instant at) {
        return !at.isBefore(expiresAt);
    }

    public void accept(UUID questId, Instant decidedAt) {
        // 아래 세 메서드의 이 검사는 실무 경로에서는 도달하지 않는다.
        // 애플리케이션이 먼저 isPending 으로 걸러서 응답을 내려주기 때문이다.
        // 그래도 남겨두는 이유는 이 애그리거트가 호출자가 검사를 거쳤다고 전제하면 안 되기 때문이고,
        // 나중에 관리자 도구나 재처리 배치 같은 다른 호출 경로가 생겼을 때 이것이 마지막 선이 된다.
        requirePending();
        this.status = SuggestionStatus.ACCEPTED;
        this.questId = questId;
        this.decidedAt = decidedAt;
    }

    public void reject(Instant decidedAt) {
        requirePending();
        this.status = SuggestionStatus.REJECTED;
        this.decidedAt = decidedAt;
    }

    // 근거 스냅샷이 낡았을 때 이 제안을 무효로 만든다.
    //
    // 이 상태 변경은 반드시 커밋되어야 한다.
    // 애플리케이션이 이 메서드를 부른 뒤 예외를 던져서 409 응답을 만들면
    // 트랜잭션이 롤백되면서 이 변경이 통째로 사라진다.
    // 그러면 제안은 계속 PENDING 이고, 다음 수락 시도에서 또 같은 판정을 받고,
    // 또 롤백된다. 영원히 끝나지 않는다.
    // 그래서 이 경로의 실패는 예외가 아니라 결과 타입으로 돌려준다.
    public void supersede(Instant decidedAt) {
        requirePending();
        this.status = SuggestionStatus.SUPERSEDED;
        this.decidedAt = decidedAt;
    }

    private void requirePending() {
        if (status != SuggestionStatus.PENDING) {
            throw new IllegalStateException("already decided suggestion: status=" + status);
        }
    }
}
