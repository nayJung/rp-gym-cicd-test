package com.workoutdone.rpgym.game.party.domain;

import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class PartyTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final Duration RECRUIT = Duration.ofHours(24);
    private static final Duration LIFETIME = Duration.ofDays(7);

    private Party party(String name){
        return party(name, PartyMetric.STEPS);
    }

    private Party party(String name, PartyMetric metric){
        return Party.create(UUID.randomUUID(), name, UUID.randomUUID(), PartyVisibility.PRIVATE, metric,
                4, NOW, RECRUIT, LIFETIME);
    }

    @DisplayName("파티 생성 직후: RECRUITING, 1/4, 마감 +24h, 수명 +7d (생성 기준), metric 확정")
    @Test
    void create(){
        Party p = party(" 아침 습관 챌린지 ", PartyMetric.ACTIVE_MINUTES);

        assertThat(p.getPartyName()).isEqualTo("아침 습관 챌린지"); // 앞뒤 공백 제거
        assertThat(p.getStatus()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(p.getMetric()).isEqualTo(PartyMetric.ACTIVE_MINUTES);
        assertThat(p.getCurrentMember()).isEqualTo(1);
        assertThat(p.getMatchingDeadlineAt()).isEqualTo(NOW.plus(RECRUIT));
        assertThat(p.getEndsAt()).isEqualTo(NOW.plus(LIFETIME));
    }

    @DisplayName("이름이 공백이거나 50자를 넘으면 생성불가")
    @Test
    void invalidName(){
        assertThatThrownBy(() -> party("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> party("a".repeat(51))).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("metric 이 없으면 생성불가 — 파티 퀘스트가 볼 지표가 없다")
    @Test
    void metricRequired(){
        assertThatThrownBy(() -> party("x", null)).isInstanceOf(IllegalArgumentException.class);
    }


    @DisplayName("마감 시각이 지나면 DB 상태가 RECRUITING이어도 모집중이 아니고, 표시 상태는 ACTIVE 다 (lazy 판정)")
    @Test
    void lazyClose(){
        Party p = party("x");
        Instant afterDeadline = NOW.plus(RECRUIT).plusSeconds(1);

        assertThat(p.isRecruiting(NOW)).isTrue();
        assertThat(p.isRecruiting(afterDeadline)).isFalse();
        assertThat(p.getStatus()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(p.displayStatus(afterDeadline)).isEqualTo(PartyStatus.ACTIVE);
    }


    @DisplayName("마지막 멤버가 나가면 DISBANDED")
    @Test
    void disbandOnLastLeave() {
        Party p = party("x");
        p.memberLeft();

        assertThat(p.getCurrentMember()).isZero();
        assertThat(p.getStatus()).isEqualTo(PartyStatus.DISBANDED);
        assertThatThrownBy(p::memberLeft).isInstanceOf(IllegalStateException.class);
    }



}
