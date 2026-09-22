package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.quest.domain.repo.QuestRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 읽기 전용. 쓰기(QuestProgressService)와 나눈 이유는 XpGrantService/XpQueryService와 같다
 * 쓰기는 트랜잭션에 합류하고 멱등키를 다루지만, 읽기는 readOnly 조회다.
 * 한 서비스에 합치면 조회하려고 주입한 빈에 판정·지급 메서드가 노출된다.
 */
@Service
@RequiredArgsConstructor
public class QuestQueryService {

    private final QuestRepository questRepository;

    /**
     * 지금 진행 중인 Quest 하나. 없으면 empty.
     * 여기서 Instant.now()를 쓴다. snapshot.measuredAt()을 쓰는 것과 다른데,
     * 판정은 언제 처리하든 결과가 같아야 하고 조회는 지금 시점에 무엇이 보이는가라
     * 기준 시각의 성격 자체가 다르기 때문이다.
     * 조회가 status=ACTIVE AND expiredAt>now 로 이미 거르므로 displayStatus(now)를 다시 부르지
     * 않는다. 걸러 온 Quest는 항상 ACTIVE다. displayStatus는 만료된 것까지 보여주는
     * 목록 조회에서 의미가 생긴다.
     */
    @Transactional(readOnly = true)
    public Optional<QuestView> findActive(UUID userId) {
        return questRepository.findActiveByUserId(userId, Instant.now())
                .map(QuestView::from);
    }
}
