package com.workoutdone.rpgym.game.ranking.application;

import com.workoutdone.rpgym.game.character.domain.LevelPolicy;
import com.workoutdone.rpgym.game.character.domain.XpClient;
import com.workoutdone.rpgym.game.ranking.domain.CharacterLevelWriter;
import com.workoutdone.rpgym.game.ranking.domain.RankingScore;
import com.workoutdone.rpgym.game.ranking.domain.RankingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingCommandService implements RankingService{

    private final XpClient xpClient;
    private final CharacterLevelWriter characterLevelWriter;
    private final RankingStore rankingStore;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onXpChanged(UUID userId){
        if (userId == null){
            throw new IllegalArgumentException("userId는 필수입니다.");
        }

        // 절대값을 매번 다시 읽는다. 이것이 멱등성
        int totalXp = xpClient.findTotalXp(userId);
        int level = LevelPolicy.levelOf(totalXp);

        characterLevelWriter.upsertLevel(userId, level);

        // ZADD 는 트랜잭션 밖의 작업이다. DB 커밋이 롤백되어도 Redis 는 되돌아가지 않는다.
        // 다만 다음 onXpChanged() 호출이 절대값으로 다시 덮어쓰므로 자연히 수렴한다.
        //rankingStore.save(userId, RankingScore.encode(level, totalXp));
        /**
         * 레디스가 안돌아가서 파티쪽 테스트 돌렸는데 unexpectedRollbackException가 나서 onXpChanged부분에서 롤백이되는것 같았다.
         * 트랜잭션이벤트리스너를 xp롤백이 되서 트랜잭션 리콰이어 새로 열어버리고 상위트랜잭션이 되니까
         */
        try{
            rankingStore.save(userId, RankingScore.encode(level, totalXp));
        }catch(Exception e){
            log.warn("랭킹 점수 반영 실패!! 다음 지급 때 복구 userId={}", userId, e);
        }

        log.debug("랭킹 갱신 userId={} level={} totalXp={}", userId, level, totalXp);
    }
}
