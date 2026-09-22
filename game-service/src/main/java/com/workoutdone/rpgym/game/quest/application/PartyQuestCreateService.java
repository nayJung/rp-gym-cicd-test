package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.quest.domain.Metric;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;
import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;
import com.workoutdone.rpgym.game.quest.domain.repo.PartyQuestMemberRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.PartyQuestRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.UserLatestSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 파티장이 파티 퀘스트를 만든다.
// 파티가 만들어질 때 자동으로 생기지 않는다. 파티 담당자와 그렇게 합의했다.
// 파티는 그냥 사람이 모인 것이고, 무엇을 얼마나 할지는 파티장이 따로 정하는 일이라
// 목표값과 지표를 누가 정하느냐는 문제가 생기지 않는 편이 낫다.
// 그래서 파티 쪽은 나에게 아무것도 보내지 않고, 파티장이 이 API 를 부른다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyQuestCreateService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);
    private static final int MAX_MEMBERS = 4;
    private static final int MAX_TITLE_LENGTH = 100;

    private final PartyQuestRepository partyQuestRepository;
    private final PartyQuestMemberRepository partyQuestMemberRepository;
    private final UserLatestSnapshotRepository userLatestSnapshotRepository;
    private final RewardPolicy rewardPolicy;

    @Transactional
    public PartyQuestCreation create(PartyQuestCreateCommand command) {
        // 사람이 지금 요청한 것이므로 서버 시계를 쓴다.
        // 이벤트를 처리할 때 측정 시각을 쓰는 것과 기준 시각의 성격이 다르다.
        Instant now = Instant.now();

        List<UUID> memberIds = command.memberUserIds();
        if (memberIds == null || memberIds.isEmpty() || memberIds.size() > MAX_MEMBERS) {
            return failed(PartyQuestCreation.Reason.INVALID_MEMBERS, command);
        }
        // 같은 사람이 두 번 들어오면 멤버 행의 유니크 제약에 걸린다.
        // 그 예외를 그대로 두면 500 이 나가므로 여기서 먼저 답한다.
        if (new HashSet<>(memberIds).size() != memberIds.size()) {
            return failed(PartyQuestCreation.Reason.INVALID_MEMBERS, command);
        }
        // 자기가 속하지 않은 파티의 퀘스트를 만들 수 없다.
        // 파티장인지까지는 여기서 확인하지 못한다. 역할은 파티 담당자 테이블에 있다.
        // 그쪽 명단을 읽게 되면 그때 파티장 확인이 함께 붙는다.
        if (!memberIds.contains(command.requesterId())) {
            return failed(PartyQuestCreation.Reason.NOT_A_MEMBER, command);
        }

        String title = command.title();
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            return failed(PartyQuestCreation.Reason.INVALID_TITLE, command);
        }

        Optional<Metric> metric = Metric.from(command.metric());
        if (metric.isEmpty()) {
            return failed(PartyQuestCreation.Reason.UNKNOWN_METRIC, command);
        }
        if (command.targetValue() <= 0) {
            return failed(PartyQuestCreation.Reason.INVALID_TARGET, command);
        }

        // 파티당 활성 퀘스트는 하나다.
        // 여러 개를 허용하면 같은 걸음이 여러 퀘스트에 동시에 들어가고,
        // 유저가 자기 활동이 어디에 얼마나 반영됐는지 알 수 없게 된다.
        if (partyQuestRepository.existsActiveByPartyId(command.partyId(), now)) {
            return failed(PartyQuestCreation.Reason.PARTY_QUEST_ALREADY_ACTIVE, command);
        }

        // 기한은 오늘 한국 시간 자정 직전이다.
        // 하루를 넘길 수 없는 이유는 건강 데이터가 자정마다 0 으로 돌아가는 당일 누적값이고,
        // 어제 누적값을 보관하는 테이블이 어디에도 없기 때문이다.
        // 며칠짜리로 만들면 날짜별 증분을 따로 쌓아야 하고, 그러면 이벤트가 하루치 통째로 유실될 때
        // 복구할 방법이 없어진다.
        Instant expiredAt = endOfDayKst(now);
        if (!expiredAt.isAfter(now)) {
            // 자정 직전 1 초 안에 요청이 들어온 경우다. 만들어도 바로 만료된다.
            return failed(PartyQuestCreation.Reason.TOO_LATE_IN_DAY, command);
        }

        PartyQuest partyQuest = partyQuestRepository.save(PartyQuest.create(
                UUID.randomUUID(),
                command.partyId(),
                title,
                metric.get(),
                command.targetValue(),
                rewardPolicy.partyQuestRewardXp(),
                now,
                expiredAt
        ));

        List<PartyQuestMember> members = partyQuestMemberRepository.saveAll(
                enroll(partyQuest.getPartyQuestId(), memberIds, metric.get()));

        log.info("파티 퀘스트 생성. partyQuestId={} partyId={} metric={} target={} 멤버={}명 기한={}",
                partyQuest.getPartyQuestId(), partyQuest.getPartyId(), partyQuest.getMetric(),
                partyQuest.getTargetVal(), members.size(), partyQuest.getExpiredAt());

        return new PartyQuestCreation.Created(PartyQuestView.from(partyQuest, members));
    }

    // 멤버 명단을 만들면서 각자의 기준값을 채운다.
    // 기준값은 내가 이미 들고 있는 유저별 최신 스냅샷에서 가져온다.
    // 파티 담당 서비스나 Health 에 물어보지 않는다. 물어보면 그쪽이 죽었을 때 퀘스트를 못 만든다.
    // 한 번도 동기화한 적 없는 유저는 스냅샷이 없어서 비워둔다.
    // 그 멤버 하나 때문에 파티 전체의 퀘스트 생성을 막는 것은 과하다.
    // 비워두면 그 멤버의 첫 이벤트가 도착할 때 확정된다.
    private List<PartyQuestMember> enroll(UUID partyQuestId, List<UUID> memberIds, Metric metric) {
        Map<UUID, UserLatestSnapshot> snapshots = userLatestSnapshotRepository
                .findAllByUserIds(memberIds).stream()
                .collect(Collectors.toMap(UserLatestSnapshot::getUserId, Function.identity()));

        return memberIds.stream()
                .map(userId -> PartyQuestMember.join(
                        UUID.randomUUID(),
                        partyQuestId,
                        userId,
                        baselineOf(snapshots.get(userId), metric)))
                .toList();
    }

    // 스냅샷이 없으면 0 이 아니라 비어 있는 값을 준다.
    // 0 으로 두면 그 유저가 오늘 이미 걸어둔 활동이 통째로 기여로 잡혀서,
    // 목표가 작으면 퀘스트가 시작하자마자 완료되고 XP 가 공짜로 나간다.
    private static Integer baselineOf(UserLatestSnapshot snapshot, Metric metric) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.toSnapshot().valueOf(metric);
    }

    private static Instant endOfDayKst(Instant now) {
        return now.atZone(KST).toLocalDate().atTime(END_OF_DAY).atZone(KST).toInstant();
    }

    private PartyQuestCreation failed(PartyQuestCreation.Reason reason, PartyQuestCreateCommand command) {
        log.info("파티 퀘스트 생성 실패. reason={} partyId={} requesterId={}",
                reason, command.partyId(), command.requesterId());
        return new PartyQuestCreation.Failed(reason);
    }
}
