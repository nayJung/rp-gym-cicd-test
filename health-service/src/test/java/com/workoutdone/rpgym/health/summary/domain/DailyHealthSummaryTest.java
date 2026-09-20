package com.workoutdone.rpgym.health.summary.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DailyHealthSummaryTest {

    @Test
    void 최초_생성시_전체달성여부는_false이고_achievedAt은_null이다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );

        assertThat(summary.isAllGoalsAchieved()).isFalse();
        assertThat(summary.getAchievedAt()).isNull();
    }

    @Test
    void 최초로_모든목표를_달성하면_true를_반환하고_achievedAt이_설정된다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );
        Instant achievedAt = Instant.parse("2026-08-30T12:10:00Z");

        boolean firstResult = summary.markAllGoalsAchieved(achievedAt);

        assertThat(firstResult).isTrue();
        assertThat(summary.isAllGoalsAchieved()).isTrue();
        assertThat(summary.getAchievedAt()).isEqualTo(achievedAt);
    }

    @Test
    void 이미_달성한_상태에서_다시_호출하면_false를_반환하고_achievedAt이_바뀌지_않는다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );
        Instant firstAchievedAt = Instant.parse("2026-08-30T12:10:00Z");
        summary.markAllGoalsAchieved(firstAchievedAt);

        boolean secondResult = summary.markAllGoalsAchieved(Instant.parse("2026-08-30T13:00:00Z"));

        assertThat(secondResult).isFalse();
        assertThat(summary.getAchievedAt()).isEqualTo(firstAchievedAt);
    }

    @Test
    void 한번도_제안한적_없으면_제안가능하다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );

        boolean due = summary.isQuestSuggestionDue(Instant.now(), Duration.ofMinutes(30));

        assertThat(due).isTrue();
    }

    @Test
    void 제안직후_주기가_안지났으면_제안불가하다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );
        Instant suggestedAt = Instant.parse("2026-08-30T12:00:00Z");
        summary.recordQuestSuggested(suggestedAt);

        boolean due = summary.isQuestSuggestionDue(
                suggestedAt.plus(Duration.ofMinutes(10)), Duration.ofMinutes(30)
        );

        assertThat(due).isFalse();
    }

    @Test
    void 주기가_지나면_다시_제안가능하다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );
        Instant suggestedAt = Instant.parse("2026-08-30T12:00:00Z");
        summary.recordQuestSuggested(suggestedAt);

        boolean due = summary.isQuestSuggestionDue(
                suggestedAt.plus(Duration.ofMinutes(31)), Duration.ofMinutes(30)
        );

        assertThat(due).isTrue();
    }

    @Test
    void recordQuestSuggested는_마지막_제안시각을_갱신한다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );
        Instant firstSuggestedAt = Instant.parse("2026-08-30T12:00:00Z");
        Instant secondSuggestedAt = Instant.parse("2026-08-30T12:40:00Z");

        summary.recordQuestSuggested(firstSuggestedAt);
        summary.recordQuestSuggested(secondSuggestedAt);

        boolean due = summary.isQuestSuggestionDue(
                secondSuggestedAt.plus(Duration.ofMinutes(10)), Duration.ofMinutes(30)
        );
        assertThat(due).isFalse();
    }

    @Test
    void 목표_미달성이면_실패처리된다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );

        boolean failed = summary.markAsFailed(Instant.now());

        assertThat(failed).isTrue();
        assertThat(summary.getFailedAt()).isNotNull();
    }

    @Test
    void 이미_달성한_건은_실패처리되지_않는다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );
        summary.markAllGoalsAchieved(Instant.now());

        boolean failed = summary.markAsFailed(Instant.now());

        assertThat(failed).isFalse();
        assertThat(summary.getFailedAt()).isNull();
    }

    @Test
    void 이미_실패처리된_건은_중복처리되지_않는다() {
        DailyHealthSummary summary = DailyHealthSummary.createFor(
                UUID.randomUUID(), LocalDate.of(2026, 8, 30), Instant.now()
        );
        Instant firstFailedAt = Instant.parse("2026-08-31T00:05:00Z");
        summary.markAsFailed(firstFailedAt);

        boolean failedAgain = summary.markAsFailed(Instant.now());

        assertThat(failedAgain).isFalse();
        assertThat(summary.getFailedAt()).isEqualTo(firstFailedAt);
    }
}