package com.workoutdone.rpgym.game.quest.application.payload;

import com.workoutdone.rpgym.game.quest.domain.aggregate.Quest;

import java.time.Instant;
import java.util.UUID;

// game.events 토픽으로 내보내는 퀘스트 완료 이벤트의 본문이다.
// 제안 이벤트와 달리 이쪽은 왕복이 없다. 알림 담당 서비스가 카드를 띄우면 그걸로 끝이고,
// 내 쪽에서 기다릴 응답도 되돌릴 상태도 없다. 알림이 실패해도 XP 는 이미 지급돼 있다.
// title 을 싣는 이유는 알림 쪽이 이 이벤트 하나만으로 카드를 그릴 수 있어야 하기 때문이다.
// 싣지 않으면 알림 쪽이 둘 중 하나를 해야 한다.
// 내 조회 API 를 부르거나, 앞서 받은 생성 이벤트를 보관해뒀다가 짝을 맞추거나.
// 앞쪽은 알림 발송이 내 서비스의 가용성에 묶이고, 뒤쪽은 알림 쪽에 상태가 생기면서
// 생성 이벤트가 하나라도 유실되면 카드가 깨진다.
// 다만 아무 값이나 실어도 되는 것은 아니다. 판별 기준은 그 값이 변하느냐다.
// 여기 실린 것은 전부 퀘스트가 만들어진 뒤로 바뀌지 않는 값이라, 중복으로 실려도 어긋날 수 없다.
// 반대로 유저의 총 XP 처럼 계속 변하는 값은 싣지 않는다.
// 그런 값을 실으면 소비하는 쪽이 델타를 더해 나가는 구현을 하게 되고,
// 이벤트 하나가 유실되는 순간 영구히 어긋난다. 그런 값은 소비하는 쪽이 다시 조회해야 한다.
public record QuestCompletedData(
        UUID questId,
        String title,
        String metric,
        int targetValue,
        int baselineValue,
        int achievedDelta,
        int rewardXp,
        Instant completedByMeasuredAt
) {

    public static QuestCompletedData from(Quest quest, int achievedDelta, Instant completedByMeasuredAt) {
        return new QuestCompletedData(
                quest.getQuestId(),
                quest.getTitle(),
                quest.getMetric().name(),
                quest.getTargetVal(),
                quest.getBaselineVal(),
                achievedDelta,
                quest.getRewardXp(),
                completedByMeasuredAt
        );
    }
}
