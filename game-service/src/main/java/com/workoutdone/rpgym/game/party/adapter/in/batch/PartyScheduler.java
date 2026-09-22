package com.workoutdone.rpgym.game.party.adapter.in.batch;


import com.workoutdone.rpgym.game.party.application.PartyLifecycleBatch;
import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;


//// 파티 생명주기 스케줄러.
/// OutboxPublishScheduler 와 같은 이유로 트랜잭션 클래스(PartyLifecycleBatch)와 분리한다
/// 스케줄러와 트랜잭션을 한 메서드에 두면 자기호출이라 프록시를 안탄다.
/// fixedDelay라서 이전 라운드가 끝난 뒤에 다음이 시작, 라운드 요약을 debug 로, 삼킨 예외를 error 로만


@Slf4j
@Component
@RequiredArgsConstructor
public class PartyScheduler {

    private final PartyLifecycleBatch batch;
    private final PartyOutboxRelay outboxRelay;

    /// 파티 outbox 릴레이. quest 의 OutboxPublishScheduler 와 같은 주기·같은 규칙이지만 테이블이 다르다.
    @Scheduled(fixedDelayString = "${rpgym.party.outbox.poll-interval:1000}")
    public void publishPendingPartyEvents(){
        try{
            int published = outboxRelay.relayOnce();
            if (published > 0){
                log.debug("파티 outbox 발행 count={}", published);
            }
        }catch(Exception e){
            log.error("파티 outbox 폴링 실패", e); // 스케줄 스레드로 새어나가면 이후 실행이 멈춤
        }
    }

    @Scheduled(fixedDelayString = "${rpgym.party.batch.close-interval:10000}")
    public void closeDueRecruiting(){
        try{
            int closed = batch.closeDueRecruiting();
            if (closed > 0){
                log.debug("모집마감 배치 라운드 closed={}", closed);
            }
        }catch(Exception e){
            log.error("모집마감 배치 실패", e); // 스케줄 스레드로 새어나가면 이후 실행이 멈춤
        }
    }

    @Scheduled(fixedDelayString = "${rpgym.party.batch.end-interval:60000}")
    public void endDuePartiesAndExpireInvitations(){
        try{
            for (UUID partyId : batch.findPartiesToEnd()){
                try{
                    batch.endParty(partyId);// 파티마다 별도 트랜잭션. 하나가 실패해도 나머지는 커밋
                }catch(Exception e){
                    log.error("파티 종료실패 - 다음 라운드에 재시도. partyId={}", partyId, e);
                }
            }
            batch.expireInvitations();// 건수 로그는 배치가 남김
        }catch(Exception e){
            log.error("파티 종료 배치 실패", e);
        }
    }

}
