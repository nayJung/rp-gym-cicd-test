package com.workoutdone.rpgym.game.quest.application.payload;

import com.workoutdone.rpgym.game.quest.domain.aggregate.QuestSuggestion;

import java.time.Instant;
import java.util.UUID;

// game.events 토픽으로 내가 내보내는 제안 이벤트의 본문이다.
// 알림 담당 서비스가 이것을 받아 Slack 카드를 그리고, 수락 버튼에 suggestionId 를 박는다.
//
// 담는 값을 카드에 필요한 것만으로 줄였다.
// 누구에게 보낼지는 이벤트 봉투의 userId 가 이미 들고 있고,
// 근거 스냅샷의 측정 시각은 내부 판정용이라 알림 쪽에서 쓸 일이 없다.
//
// 여기 실린 값은 전부 제안이 만들어진 뒤로 바뀌지 않는 것들이다.
// 바뀌는 값을 이벤트에 실으면 소비하는 쪽이 낡은 값을 보게 되므로,
// 그런 값은 싣지 않고 소비하는 쪽이 다시 조회하게 해야 한다.
public record QuestToNotification(
        UUID suggestionId,
        String title,
        String metric,
        int targetValue,
        Instant expiresAt
) {

    public static QuestToNotification from(QuestSuggestion suggestion) {
        return new QuestToNotification(
                suggestion.getSuggestionId(),
                suggestion.getTitle(),
                suggestion.getMetric().name(),
                suggestion.getTargetVal(),
                suggestion.getExpiresAt()
        );
    }
}
