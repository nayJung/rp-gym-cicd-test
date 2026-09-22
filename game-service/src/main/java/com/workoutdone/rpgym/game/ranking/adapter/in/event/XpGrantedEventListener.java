package com.workoutdone.rpgym.game.ranking.adapter.in.event;

import com.workoutdone.rpgym.game.ranking.application.RankingService;
import com.workoutdone.rpgym.game.xp.application.XpGranted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * XP 지급을 랭킹 갱신으로 잇는 인바운드 어댑터.
 *
 * <p>xp 컨텍스트는 랭킹을 모른다. {@link XpGranted} 를 발행할 뿐이고 이쪽이 구독한다.
 * 의존은 ranking → xp 한 방향이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XpGrantedEventListener {

    private final RankingService rankingService;

    /**
     * AFTER_COMMIT 이라 XP 지급 트랜잭션이 커밋된 뒤에만 실행된다.
     *
     * <p>REQUIRES_NEW 인 이유 — 이 시점에는 바깥 트랜잭션 리소스가 아직 바인딩돼 있어서,
     * 기본 전파(REQUIRED)로 두면 이미 커밋된 트랜잭션에 합류해 upsertLevel() 이 조용히 유실된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(XpGranted event) {
        try {
            rankingService.onXpChanged(event.userId());
        } catch (Exception e) {
            /*
             * 랭킹 갱신 실패가 XP 지급을 되돌리거나 컨슈머를 멈춰선 안 된다.
             * AFTER_COMMIT 리스너의 예외는 커밋 호출자에게 전파되므로 여기서 반드시 삼킨다.
             * (삼키지 않으면 Kafka offset 이 안 잡혀 해당 파티션이 멈춘다)
             *
             * 랭킹은 절대값을 다시 읽어 덮어쓰는 구조라, 다음 XpGranted 가 알아서 바로잡는다.
             * 즉시 복구가 필요하면 RankingRebuildJob 을 수동 실행한다.
             */
            log.error("랭킹 갱신 실패. userId={}", event.userId(), e);
        }
    }
}
