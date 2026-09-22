package com.workoutdone.rpgym.game.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyWeekTest {

    @DisplayName("KST 기준 월요일 00:00 부터 다음 월요일 00:00 전까지가 한 주다")
    @Test
    void weekBoundary() {
        // 2026-09-15 (화) 09:00 KST = 00:00Z
        PartyWeek week = PartyWeek.of(Instant.parse("2026-09-15T00:00:00Z"));

        assertThat(week.key()).isEqualTo("2026-W38");
        assertThat(week.start()).isEqualTo(Instant.parse("2026-09-13T15:00:00Z"));   // 09-14 00:00 KST
        assertThat(week.end()).isEqualTo(Instant.parse("2026-09-20T15:00:00Z"));     // 09-21 00:00 KST
        assertThat(week.contains(Instant.parse("2026-09-20T14:59:59Z"))).isTrue();
        assertThat(week.contains(Instant.parse("2026-09-20T15:00:00Z"))).isFalse();
    }

    @DisplayName("일요일 밤 11시 KST 는 아직 같은 주다")
    @Test
    void sundayNightKst() {
        // 2026-09-20 (일) 23:00 KST = 14:00Z
        assertThat(PartyWeek.of(Instant.parse("2026-09-20T14:00:00Z")).key()).isEqualTo("2026-W38");
    }

    @DisplayName("parse 는 of 와 같은 경계를 만든다")
    @Test
    void parseRoundTrip() {
        PartyWeek a = PartyWeek.of(Instant.parse("2026-09-15T00:00:00Z"));
        PartyWeek b = PartyWeek.parse("2026-W38");
        assertThat(b).isEqualTo(a);
        assertThatThrownBy(() -> PartyWeek.parse("2026-38")).isInstanceOf(IllegalArgumentException.class);
    }
}
