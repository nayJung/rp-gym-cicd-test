package com.workoutdone.rpgym.game.achievement.domain;

import com.workoutdone.rpgym.game.achievement.domain.aggregate.UserAchievement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하루치를 세는 규칙. 입력은 DAILY_GOAL_COMPLETED 하나뿐이라
 * "언제 세고 언제 안 세는가" 와 "STREAK 가 언제 끊기는가" 두 가지가 전부다.
 */
class UserAchievementTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = D1.plusDays(1);
    private static final LocalDate D3 = D1.plusDays(2);
    private static final LocalDate D5 = D1.plusDays(4);
    private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");

    private UserAchievement fresh() {
        return UserAchievement.start(UUID.randomUUID(), OwnerType.USER, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("COUNT — 기준값 1이면 첫날에 바로 ACHIEVED 이고 achievedAt 은 Health 가 준 값이다")
    void countFirstDayAchieves() {
        UserAchievement ua = fresh();

        CountResult r = ua.count(D1, ConditionType.DAILY_GOAL_COUNT, 1, AT);

        assertThat(r).isEqualTo(CountResult.ACHIEVED);
        assertThat(ua.isAchieved()).isTrue();
        assertThat(ua.getCurrentValue()).isEqualTo(1);
        assertThat(ua.getAchievedAt()).isEqualTo(AT);
        assertThat(ua.getLastCountedDate()).isEqualTo(D1);
    }

    @Test
    @DisplayName("COUNT — 하루 빠져도 누적은 이어진다")
    void countSurvivesGap() {
        UserAchievement ua = fresh();
        ua.count(D1, ConditionType.DAILY_GOAL_COUNT, 3, AT);
        ua.count(D3, ConditionType.DAILY_GOAL_COUNT, 3, AT);

        assertThat(ua.getCurrentValue()).isEqualTo(2);
        assertThat(ua.isAchieved()).isFalse();
    }

    @Test
    @DisplayName("STREAK — 연속 3일이면 3일째에 ACHIEVED")
    void streakThreeDays() {
        UserAchievement ua = fresh();

        assertThat(ua.count(D1, ConditionType.DAILY_GOAL_STREAK, 3, AT)).isEqualTo(CountResult.PROGRESSED);
        assertThat(ua.count(D2, ConditionType.DAILY_GOAL_STREAK, 3, AT)).isEqualTo(CountResult.PROGRESSED);
        assertThat(ua.count(D3, ConditionType.DAILY_GOAL_STREAK, 3, AT)).isEqualTo(CountResult.ACHIEVED);
        assertThat(ua.getCurrentValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("STREAK — 하루 빠지면 1로 되돌아간다")
    void streakResetsOnGap() {
        UserAchievement ua = fresh();
        ua.count(D1, ConditionType.DAILY_GOAL_STREAK, 7, AT);
        ua.count(D2, ConditionType.DAILY_GOAL_STREAK, 7, AT);

        CountResult r = ua.count(D5, ConditionType.DAILY_GOAL_STREAK, 7, AT);

        assertThat(r).isEqualTo(CountResult.PROGRESSED);
        assertThat(ua.getCurrentValue()).isEqualTo(1);
        assertThat(ua.getLastCountedDate()).isEqualTo(D5);
    }

    @Test
    @DisplayName("같은 날짜가 두 번 오면 두 번째는 SKIPPED — Health 재전송 · 컨슈머 재시도 멱등")
    void sameDateIsSkipped() {
        UserAchievement ua = fresh();
        ua.count(D1, ConditionType.DAILY_GOAL_COUNT, 5, AT);

        CountResult r = ua.count(D1, ConditionType.DAILY_GOAL_COUNT, 5, AT);

        assertThat(r).isEqualTo(CountResult.SKIPPED);
        assertThat(ua.getCurrentValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("과거 날짜는 SKIPPED — 순서 역전이 STREAK 를 리셋하면 안 된다")
    void pastDateIsSkipped() {
        UserAchievement ua = fresh();
        ua.count(D1, ConditionType.DAILY_GOAL_STREAK, 7, AT);
        ua.count(D2, ConditionType.DAILY_GOAL_STREAK, 7, AT);
        ua.count(D3, ConditionType.DAILY_GOAL_STREAK, 7, AT);

        CountResult r = ua.count(D1, ConditionType.DAILY_GOAL_STREAK, 7, AT);

        assertThat(r).isEqualTo(CountResult.SKIPPED);
        assertThat(ua.getCurrentValue()).isEqualTo(3);
        assertThat(ua.getLastCountedDate()).isEqualTo(D3);
    }

    @Test
    @DisplayName("이미 ACHIEVED 면 이후 날짜도 SKIPPED — 재지급 경로가 열리지 않는다")
    void achievedIsTerminal() {
        UserAchievement ua = fresh();
        ua.count(D1, ConditionType.DAILY_GOAL_COUNT, 1, AT);

        CountResult r = ua.count(D2, ConditionType.DAILY_GOAL_COUNT, 1, AT.plusSeconds(86_400));

        assertThat(r).isEqualTo(CountResult.SKIPPED);
        assertThat(ua.getCurrentValue()).isEqualTo(1);
        assertThat(ua.getAchievedAt()).isEqualTo(AT);
    }
}
