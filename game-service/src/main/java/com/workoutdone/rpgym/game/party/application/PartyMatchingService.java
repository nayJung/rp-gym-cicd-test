package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.outbox.application.PartyOutboxRecorder;
import com.workoutdone.rpgym.game.party.domain.PartyAggregateType;
import com.workoutdone.rpgym.game.party.domain.PartyEventType;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.application.payload.PartyMemberJoinedData;
import com.workoutdone.rpgym.game.party.application.view.MatchingResultView;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * "대기열" 은 없다. 빈 파티가 없으면 요청자를 OWNER 로 하는 PUBLIC 파티를 만들고 RECRUITING 으로 둔다.
 * 그 파티가 곧 대기 상태이고 다음 사람의 탐색 대상이다. 대기자와 파티가 같은 객체라 어긋날 수 없다.
 *
 * 매칭은 같은 metric 끼리만 한다. 걸음 파티에 칼로리 원하는 사람을 넣으면 파티 퀘스트가 그 사람에겐 의미가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyMatchingService {

    static final String DEFAULT_PARTY_NAME = "자동 매칭 파티";

    private final PartyRepository partyRepository;
    private final MemberEnroller enroller;
    private final PartyCloser closer;
    private final PartyOutboxRecorder outboxRecorder;
    private final ApplicationEventPublisher events;
    private final PartyProperties props;
    private final Clock clock;

    @Transactional
    public MatchingResultView match(UUID userId, String partyNameIfCreated, PartyMetric metric) {
        enroller.assertNotInParty(userId);
        Instant now = clock.instant();

        // 같은 metric 중 거의 찬 파티부터 최대 N개. 경쟁에서 밀리면 재조회 없이 다음 후보로.
        for (Party candidate : partyRepository.findMatchingCandidates(metric, now, props.matchingCandidates())) {
            if (partyRepository.reserveSeat(candidate.getId(), now)) {
                return joined(candidate.getId(), userId, now);
            }
            log.debug("자동 매칭 후보 경합에서 밀림 — 다음 후보. partyId={} userId={}", candidate.getId(), userId);
        }
        return created(userId, partyNameIfCreated, metric, now);
    }

    private MatchingResultView joined(UUID partyId, UUID userId, Instant now) {
        PartyMember member = enroller.enroll(PartyMember.member(UUID.randomUUID(), partyId, userId, now));
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalStateException("자리를 확보한 파티가 없습니다: " + partyId));

        outboxRecorder.append(
                PartyAggregateType.PARTY_MEMBER,
                member.getId(),
                PartyEventType.PARTY_MEMBER_JOINED,
                userId,
                now,
                new PartyMemberJoinedData(partyId, member.getId(), userId, member.getRole().name(),
                        party.getCurrentMember(), party.getMaxMember(), now));

        log.info("파티 입장. partyId={} userId={} via=MATCHING metric={} members={}/{}",
                partyId, userId, party.getMetric(), party.getCurrentMember(), party.getMaxMember());

        PartyStatus status = party.getStatus();
        if (party.isFull()) {
            closer.close(partyId, now, PartyCloser.Trigger.FULL);
            status = PartyStatus.ACTIVE;
        }

        return new MatchingResultView(MatchingResultView.Result.MATCHED, partyId, party.getPartyName(),
                party.getMetric(), status,
                party.getCurrentMember(), party.getMaxMember(), party.getMatchingDeadlineAt());
    }

    private MatchingResultView created(UUID userId, String partyName, PartyMetric metric, Instant now) {
        String name = (partyName == null || partyName.isBlank()) ? DEFAULT_PARTY_NAME : partyName;
        Party party = partyRepository.save(Party.create(
                UUID.randomUUID(), name, userId, PartyVisibility.PUBLIC, metric,
                props.maxMember(), now, props.recruitDuration(), props.lifetime()));
        enroller.enroll(PartyMember.owner(UUID.randomUUID(), party.getId(), userId, now));

        // [비활성 — #122] 직접 생성과 같은 이유로 멈춰 뒀다. PartyCommandService.create() 의 주석 참고.
        // 매칭으로 만들어진 파티도 모집 마감 때 PartyCloser 가 PARTY_MATCHED 를 내보낸다.
        // events.publishEvent(new PartyQuestRequested(
        //         party.getId(), userId, party.getMetric(),
        //         now, party.getMatchingDeadlineAt(), party.getEndsAt()));

        log.info("파티 생성. partyId={} ownerId={} visibility=PUBLIC via=MATCHING metric={} members={}/{} matchingDeadlineAt={}",
                party.getId(), userId, party.getMetric(), party.getCurrentMember(), party.getMaxMember(),
                party.getMatchingDeadlineAt());
        return new MatchingResultView(MatchingResultView.Result.WAITING, party.getId(), party.getPartyName(),
                party.getMetric(), party.getStatus(),
                party.getCurrentMember(), party.getMaxMember(), party.getMatchingDeadlineAt());
    }
}
