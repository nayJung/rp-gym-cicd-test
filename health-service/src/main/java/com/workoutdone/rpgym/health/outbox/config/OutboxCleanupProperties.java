package com.workoutdone.rpgym.health.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Outbox 정리 배치 설정.
 *
 * 발행이 끝난(PUBLISHED) 행만 삭제 대상이다.
 * FAILED는 원인 분석과 수동 재처리 대상이므로 건드리지 않고,
 * PENDING은 아직 발행되지 않았으므로 당연히 제외한다.
 */
@ConfigurationProperties(prefix = "rpgym.outbox.cleanup")
public record OutboxCleanupProperties(

        // 삭제 대상으로 삼기까지의 보관 기간
        @DefaultValue("7d") Duration retention,

        // 한 트랜잭션에서 삭제할 최대 건수. 길어지면 락 점유 시간이 늘어난다
        @DefaultValue("500") int batchSize,

        /*
         * 1회 실행 시간 상한.
         *
         * 건수가 아니라 시간으로 끊는다. 상한을 두는 이유가 "배치가 너무 오래 돌면 안 된다"이므로,
         * DB가 빠르든 느리든 같은 시간에 끝나는 쪽이 의도에 맞는다.
         * 건수로 끊으면 유입량이 상한보다 많을 때 영영 따라잡지 못한다.
         *
         * 상한에 걸려 남은 대상은 다음 주기가 이어서 처리한다.
         */
        @DefaultValue("10m") Duration maxRunDuration
) {
}
