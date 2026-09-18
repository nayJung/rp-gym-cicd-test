package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;
import com.workoutdone.rpgym.game.party.domain.InvitationCloseReason;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.application.payload.PartyEndedData;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyWeek;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyInvitation;
import com.workoutdone.rpgym.game.party.domain.repo.PartyInvitationRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 시간이 되면 스스로 전이하는 세 가지. 스케줄러(PartyScheduler)가 부른다.
 *
 * 정확성은 lazy 판정(isRecruiting · isExpired · reserveSeat 의 deadline 조건)이 이미 보장한다.
 * 이 배치는 DB 의 status 컬럼을 실제 상태로 맞추는 "정리" 다. 배치가 죽어도 틀린 응답은 안 나가고,
 * 배치만 두면 그게 죽었을 때 초과 입장이 생기므로 둘 다 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyLifecycleBatch {

    private static final int BATCH_LIMIT = 100;

    private final PartyRepository partyRepository;
    private final PartyMemberRepository memberRepository;
    private final PartyInvitationRepository invitationRepository;
    private final PartyCloser closer;
    private final PartyInvitationCloser invitationCloser;
    private final PartyOutboxRecorder outboxRecorder;
    private final ApplicationEventPublisher events;   // PartyEnded → ranking 이 듣는다. party 는 누가 듣는지 모른다
    private final Clock clock;

    /** 마감 시각이 지난 RECRUITING → ACTIVE. 현재 인원으로 시작한다 (1명이어도). */
    @Transactional
    public int closeDueRecruiting() {
        Instant now = clock.instant();
        int closed = 0;
        for (Party party : partyRepository.findRecruitingPastDeadline(now, BATCH_LIMIT)) {
            // "파티 모집 마감. by=DEADLINE" 은 PartyCloser 가 파티마다 남긴다
            if (closer.close(party.getId(), now, PartyCloser.Trigger.DEADLINE).isPresent()) {
                closed++;
            }
        }
        return closed;
    }

    /** 종료 대상 id 목록. 파티마다 별도 트랜잭션으로 endParty 를 부르기 위해 id 만 돌려준다. */
    @Transactional(readOnly = true)
    public List<UUID> findPartiesToEnd() {
        return partyRepository.findActivePastEnd(clock.instant(), BATCH_LIMIT).stream().map(Party::getId).toList();
    }

    /**
     * 파티 한 개 종료. 한 트랜잭션.
     *
     * 1. 멤버 전원 LEFT (left_at = ends_at)      → 1인 1파티 제약 해제, 새 파티로 갈 수 있게
     * 2. 주간 XP 합산 (PARTY_ENDED 페이로드용)    → left_at 이 박힌 뒤라 ends_at 이후 XP 는 빠진다
     * 3. ACTIVE → ENDED
     * 4. Outbox PARTY_ENDED
     * 5. Spring 이벤트 PartyEnded                → 커밋 뒤 ranking 이 받아 ZSET 최종 재계산 (7단계)
     *
     * 랭킹 저장소를 여기서 직접 부르지 않는다. party → ranking 의존이 생기면 ranking → party 와 순환이다.
     */
    @Transactional
    public void endParty(UUID partyId) {
        Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null || party.getStatus() != PartyStatus.ACTIVE) {
            log.debug("파티 종료 건너뜀 — 이미 처리됨. partyId={} status={}", partyId, party == null ? null : party.getStatus());
            return;   // 다른 인스턴스가 먼저 끝냈다
        }
        Instant endedAt = party.getEndsAt();
        List<UUID> members = memberRepository.findActiveUserIdsByPartyId(partyId);

        memberRepository.leaveAllActive(partyId, endedAt);
        PartyWeek week = PartyWeek.of(endedAt);
        long weeklyXp = memberRepository.sumWeeklyXp(partyId, week.start(), week.end());
        party.end();
        partyRepository.save(party);

        outboxRecorder.append(
                PartyAggregateType.PARTY,
                partyId,
                PartyEventType.PARTY_ENDED,
                party.getOwnerId(),
                endedAt,
                new PartyEndedData(partyId, party.getPartyName(), members, weeklyXp, endedAt));
        events.publishEvent(new PartyEnded(partyId, endedAt));

        log.info("파티 종료. partyId={} members={} week={} weeklyXp={} endedAt={}",
                partyId, members.size(), week.key(), weeklyXp, endedAt);
    }

    /**
     * TTL 이 지난 초대를 EXPIRED 로 닫는다.
     *
     * 한 문장 UPDATE 로 한꺼번에 바꾸지 않고 행을 읽어 건별로 닫는다 — 초대마다
     * PARTY_INVITATION_CLOSED 를 실어야 알림이 그 사람 슬랙의 버튼을 거둘 수 있기 때문이다.
     * 한 라운드 BATCH_LIMIT 건으로 끊으므로 밀린 만큼은 다음 라운드가 가져간다.
     */
    @Transactional
    public int expireInvitations() {
        Instant now = clock.instant();
        List<PartyInvitation> due = invitationRepository.findPendingExpired(now, BATCH_LIMIT);
        if (due.isEmpty()) {
            return 0;
        }

        // 페이로드의 partyName 용. 초대 N 건이 같은 파티를 가리켜도 조회는 한 번이다.
        Map<UUID, String> partyNames = partyRepository.findAllByIds(
                        due.stream().map(PartyInvitation::getPartyId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Party::getId, Party::getPartyName));

        int expired = 0;
        for (PartyInvitation invitation : due) {
            if (invitationCloser.close(invitation, partyNames.get(invitation.getPartyId()),
                    InvitationCloseReason.EXPIRED, now)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("파티 초대 만료. expired={} scanned={}", expired, due.size());   // 건별 로그는 Closer 가 남긴다
        }
        return expired;
    }


}
