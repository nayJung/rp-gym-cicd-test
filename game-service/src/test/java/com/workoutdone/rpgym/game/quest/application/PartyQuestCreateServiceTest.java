package com.workoutdone.rpgym.game.quest.application;

import com.workoutdone.rpgym.game.quest.domain.Metric;
import com.workoutdone.rpgym.game.quest.domain.QuestStatus;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuest;
import com.workoutdone.rpgym.game.quest.domain.aggregate.PartyQuestMember;
import com.workoutdone.rpgym.game.quest.domain.aggregate.UserLatestSnapshot;
import com.workoutdone.rpgym.game.quest.domain.repo.PartyQuestMemberRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.PartyQuestRepository;
import com.workoutdone.rpgym.game.quest.domain.repo.UserLatestSnapshotRepository;
import com.workoutdone.rpgym.game.quest.domain.vo.Snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartyQuestCreateServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final UUID PARTY_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER_B = UUID.randomUUID();
    private static final UUID MEMBER_C = UUID.randomUUID();
    private static final UUID MEMBER_D = UUID.randomUUID();

    private PartyQuestRepository partyQuestRepository;
    private PartyQuestMemberRepository memberRepository;
    private UserLatestSnapshotRepository snapshotRepository;
    private PartyQuestCreateService service;

    @BeforeEach
    void setUp() {
        partyQuestRepository = mock(PartyQuestRepository.class);
        memberRepository = mock(PartyQuestMemberRepository.class);
        snapshotRepository = mock(UserLatestSnapshotRepository.class);
        service = new PartyQuestCreateService(
                partyQuestRepository, memberRepository, snapshotRepository, new RewardPolicy());

        when(partyQuestRepository.existsActiveByPartyId(any(), any())).thenReturn(false);
        when(partyQuestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(memberRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        // 네 명 중 셋만 동기화한 적이 있다
        when(snapshotRepository.findAllByUserIds(any())).thenReturn(List.of(
                snapshotOf(OWNER, 3000), snapshotOf(MEMBER_B, 1200), snapshotOf(MEMBER_C, 500)));
    }

    private UserLatestSnapshot snapshotOf(UUID userId, int steps) {
        return UserLatestSnapshot.create(userId,
                new Snapshot(LocalDate.now(KST), Instant.now(), steps, 0, 0));
    }

    private PartyQuestCreateCommand command(String title, String metric, int target, List<UUID> members) {
        return new PartyQuestCreateCommand(PARTY_ID, OWNER, title, metric, target, members);
    }

    private PartyQuestCreateCommand validCommand() {
        return command("퇴근길 함께 4000보", "STEPS", 4000, List.of(OWNER, MEMBER_B, MEMBER_C, MEMBER_D));
    }

    private static PartyQuestCreation.Reason reasonOf(PartyQuestCreation creation) {
        return assertInstanceOf(PartyQuestCreation.Failed.class, creation).reason();
    }

    @Test
    @DisplayName("생성되면 멤버 수만큼 행이 생기고 기준값이 저장된 스냅샷에서 온다")
    void 기준값은_스냅샷에서_온다() {
        ArgumentCaptor<List<PartyQuestMember>> saved = ArgumentCaptor.forClass(List.class);

        PartyQuestCreation creation = service.create(validCommand());

        assertInstanceOf(PartyQuestCreation.Created.class, creation);
        verify(memberRepository).saveAll(saved.capture());
        List<PartyQuestMember> members = saved.getValue();
        assertEquals(4, members.size());
        assertEquals(3000, members.get(0).getBaselineVal());
        assertEquals(1200, members.get(1).getBaselineVal());
        assertEquals(500, members.get(2).getBaselineVal());
        // 모두 기여 0 에서 시작한다
        assertEquals(0, members.get(0).getContributedVal());
    }

    @Test
    @DisplayName("한 번도 동기화한 적 없는 멤버는 기준값을 비워둔다 — 0으로 두면 공짜 완료가 난다")
    void 스냅샷이_없는_멤버는_기준값이_비어_있다() {
        ArgumentCaptor<List<PartyQuestMember>> saved = ArgumentCaptor.forClass(List.class);

        service.create(validCommand());

        verify(memberRepository).saveAll(saved.capture());
        PartyQuestMember noSnapshot = saved.getValue().get(3);
        assertEquals(MEMBER_D, noSnapshot.getUserId());
        // 여기를 0 으로 두면 그 유저가 오늘 이미 걸어둔 만큼이 통째로 기여로 잡힌다
        assertNull(noSnapshot.getBaselineVal());
    }

    @Test
    @DisplayName("기한은 오늘 한국 시간 자정 직전이다 — 누적값이 자정에 0으로 돌아가기 때문이다")
    void 기한은_당일_자정_직전이다() {
        ArgumentCaptor<PartyQuest> saved = ArgumentCaptor.forClass(PartyQuest.class);

        service.create(validCommand());

        verify(partyQuestRepository).save(saved.capture());
        LocalTime kstTime = saved.getValue().getExpiredAt().atZone(KST).toLocalTime();
        assertEquals(LocalTime.of(23, 59, 59), kstTime);
        assertEquals(QuestStatus.ACTIVE, saved.getValue().getStatus());
        assertEquals(Metric.STEPS, saved.getValue().getMetric());
    }

    @Test
    @DisplayName("보상은 요청에 없다 — 정책이 정한다")
    void 보상은_정책이_정한다() {
        ArgumentCaptor<PartyQuest> saved = ArgumentCaptor.forClass(PartyQuest.class);

        service.create(validCommand());

        verify(partyQuestRepository).save(saved.capture());
        // 파티장이 정할 수 있으면 원하는 만큼 XP 를 만들어낼 수 있다
        assertEquals(new RewardPolicy().partyQuestRewardXp(), saved.getValue().getRewardXp());
    }

    @Test
    @DisplayName("인원이 없거나 정원을 넘으면 만들지 않는다")
    void 인원_수가_틀리면_막는다() {
        assertEquals(PartyQuestCreation.Reason.INVALID_MEMBERS,
                reasonOf(service.create(command("제목", "STEPS", 4000, List.of()))));

        assertEquals(PartyQuestCreation.Reason.INVALID_MEMBERS,
                reasonOf(service.create(command("제목", "STEPS", 4000,
                        List.of(OWNER, MEMBER_B, MEMBER_C, MEMBER_D, UUID.randomUUID())))));

        verify(partyQuestRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 사람이 두 번 들어 있으면 만들지 않는다 — 멤버 행 유니크 제약보다 먼저 답한다")
    void 중복_멤버를_막는다() {
        assertEquals(PartyQuestCreation.Reason.INVALID_MEMBERS,
                reasonOf(service.create(command("제목", "STEPS", 4000, List.of(OWNER, OWNER)))));

        verify(partyQuestRepository, never()).save(any());
    }

    @Test
    @DisplayName("요청자가 명단에 없으면 만들지 않는다")
    void 남의_파티_퀘스트는_못_만든다() {
        assertEquals(PartyQuestCreation.Reason.NOT_A_MEMBER,
                reasonOf(service.create(command("제목", "STEPS", 4000, List.of(MEMBER_B, MEMBER_C)))));

        verify(partyQuestRepository, never()).save(any());
    }

    @Test
    @DisplayName("지표가 세 종류 밖이면 만들지 않는다")
    void 지표가_틀리면_막는다() {
        assertEquals(PartyQuestCreation.Reason.UNKNOWN_METRIC,
                reasonOf(service.create(command("제목", "SLEEP_HOURS", 4000,
                        List.of(OWNER, MEMBER_B)))));
    }

    @Test
    @DisplayName("목표가 0 이하면 만들지 않는다 — 첫 스냅샷에서 바로 완료되어 XP가 공짜로 나간다")
    void 목표가_0_이하면_막는다() {
        assertEquals(PartyQuestCreation.Reason.INVALID_TARGET,
                reasonOf(service.create(command("제목", "STEPS", 0, List.of(OWNER, MEMBER_B)))));
    }

    @Test
    @DisplayName("제목이 비었거나 100자를 넘으면 만들지 않는다 — 사람이 입력한 값이라 자르지 않고 거절한다")
    void 제목이_틀리면_막는다() {
        assertEquals(PartyQuestCreation.Reason.INVALID_TITLE,
                reasonOf(service.create(command("  ", "STEPS", 4000, List.of(OWNER)))));

        assertEquals(PartyQuestCreation.Reason.INVALID_TITLE,
                reasonOf(service.create(command("가".repeat(101), "STEPS", 4000, List.of(OWNER)))));
    }

    @Test
    @DisplayName("이미 진행 중인 파티 퀘스트가 있으면 만들지 않는다")
    void 이미_진행_중이면_막는다() {
        when(partyQuestRepository.existsActiveByPartyId(any(), any())).thenReturn(true);

        assertEquals(PartyQuestCreation.Reason.PARTY_QUEST_ALREADY_ACTIVE,
                reasonOf(service.create(validCommand())));

        verify(partyQuestRepository, never()).save(any());
    }
}
