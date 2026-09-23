package com.workoutdone.rpgym.game.xp.application;

import com.workoutdone.rpgym.game.xp.domain.SourceType;
import com.workoutdone.rpgym.game.xp.domain.aggregate.Wallet;
import com.workoutdone.rpgym.game.xp.domain.aggregate.XpLedger;
import com.workoutdone.rpgym.game.xp.domain.repo.WalletRepository;
import com.workoutdone.rpgym.game.xp.domain.repo.XpLedgerRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class XpGrantService {
    private final XpLedgerRepository xpLedgerRepository;
    private final WalletRepository walletRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public XpLedger grant(UUID userId, SourceType sourceType, UUID sourceId, int amount, Instant occurredAt) {
        XpLedger ledger = xpLedgerRepository.save(
                XpLedger.create(UUID.randomUUID(), userId, sourceType, sourceId, amount, occurredAt));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> Wallet.create(userId));
        wallet.addXp(amount);
        walletRepository.save(wallet);

        // 구독자는 커밋이 끝난 뒤에 받는다(@TransactionalEventListener AFTER_COMMIT).
        // 그래서 구독자 쪽이 실패해도 이 트랜잭션은 되돌아가지 않는다.
        // 직접 호출로 바꾸면 redis 장애가 XP 지급을 롤백시킨다 -- 재계산 가능한 것 때문에
        // 재계산 불가능한 원본을 잃는 셈이다.
        events.publishEvent(new XpGranted(userId));

        return ledger;
    }
}
