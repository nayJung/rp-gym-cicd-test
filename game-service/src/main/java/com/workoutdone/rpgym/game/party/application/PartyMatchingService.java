package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.outbox.application.OutboxRecorder;
import com.workoutdone.rpgym.game.outbox.domain.AggregateType;
import com.workoutdone.rpgym.game.outbox.domain.OutboxEventType;
import com.workoutdone.rpgym.game.party.application.payload.PartyMemberJoinedData;
import com.workoutdone.rpgym.game.party.application.view.MatchingResultView;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * "대기열" 은 없다. 빈 파티가 없으면 요청자를 OWNER 로 하는 PUBLIC 파티를 만들고 RECRUITING 으로 둔다.
 * 그 파티가 곧 대기 상태이고 다음 사람의 탐색 대상이다. 대기자와 파티가 같은 객체라 어긋날 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyMatchingService {

    static final String DEFAULT_PARTY_NAME = "자동 매칭 파티";

    private final PartyRepository partyRepository;
    private final MemberEnroller enroller;
    private final PartyCloser closer;
    private final OutboxRecorder outboxRecorder;
    private final PartyProperties props;
    private final Clock clock;

    @Transactional
    public MatchingResultView match(UUID userId, String partyNameIfCreated) {
        enroller.assertNotInParty(userId);
        Instant now = clock.instant();

        // 거의 찬 파티부터 최대 N개. 경쟁에서 밀리면 재조회 없이 다음 후보로.
        for (Party candidate : partyRepository.findMatchingCandidates(now, props.matchingCandidates())) {
            if (partyRepository.reserveSeat(candidate.getId(), now)) {
                return joined(candidate.getId(), userId, now);
            }
            log.debug("자동 매칭 후보 경합에서 밀림 — 다음 후보. partyId={} userId={}", candidate.getId(), userId);
        }
        return created(userId, partyNameIfCreated, now);
    }

    private MatchingResultView joined(UUID partyId, UUID userId, Instant now) {
        PartyMember member = enroller.enroll(PartyMember.member(UUID.randomUUID(), partyId, userId, now));
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalStateException("자리를 확보한 파티가 없습니다: " + partyId));

        outboxRecorder.append(
                AggregateType.PARTY_MEMBER,
                member.getId(),
                OutboxEventType.PARTY_MEMBER_JOINED,
                userId,
                now,
                new PartyMemberJoinedData(partyId, member.getId(), userId, member.getRole().name(),
                        party.getCurrentMember(), party.getMaxMember(), now));

        log.info("파티 입장. partyId={} userId={} via=MATCHING members={}/{}",
                partyId, userId, party.getCurrentMember(), party.getMaxMember());

        PartyStatus status = party.getStatus();
        if (party.isFull()) {
            closer.close(partyId, now, PartyCloser.Trigger.FULL);
            status = PartyStatus.ACTIVE;
        }

        return new MatchingResultView(MatchingResultView.Result.MATCHED, partyId, party.getPartyName(), status,
                party.getCurrentMember(), party.getMaxMember(), party.getMatchingDeadlineAt());
    }

    private MatchingResultView created(UUID userId, String partyName, Instant now) {
        String name = (partyName == null || partyName.isBlank()) ? DEFAULT_PARTY_NAME : partyName;
        Party party = partyRepository.save(Party.create(
                UUID.randomUUID(), name, userId, PartyVisibility.PUBLIC,
                props.maxMember(), now, props.recruitDuration(), props.lifetime()));
        enroller.enroll(PartyMember.owner(UUID.randomUUID(), party.getId(), userId, now));

        log.info("파티 생성. partyId={} ownerId={} visibility=PUBLIC via=MATCHING members={}/{} matchingDeadlineAt={}",
                party.getId(), userId, party.getCurrentMember(), party.getMaxMember(), party.getMatchingDeadlineAt());
        return new MatchingResultView(MatchingResultView.Result.WAITING, party.getId(), party.getPartyName(),
                party.getStatus(), party.getCurrentMember(), party.getMaxMember(), party.getMatchingDeadlineAt());
    }
}
