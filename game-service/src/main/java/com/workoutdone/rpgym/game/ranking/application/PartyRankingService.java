//package com.workoutdone.rpgym.game.ranking.application;
//
//import com.workoutdone.rpgym.game.party.domain.PartyWeek;
//import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
//import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
//import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
//import com.workoutdone.rpgym.game.ranking.domain.PartyRankingStore;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Clock;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class PartyRankingService {
//
//    private static final int MIN_SIZE = 1;
//    private static final int MAX_SIZE = 100;
//
//    private final PartyRepository partyRepository;
//    private final PartyMemberRepository memberRepository;
//    private final PartyRankingStore rankingStore;
//    private final Clock clock;
//
//    ////XpGranted 구독 진입점. 소속파티가 없으면 아무것도 안함.
//    @Transactional
//    public void onXpChanged(UUID userId){
//        memberRepository.findActiveByUserId(userId).ifPresentOrElse(
//                m -> recalculate(m.getPartyId(), clock.instant()),
//                () -> log.debug("파티 랭킹갱신 건너뜀 - 소속파티 없음. userId={}", userId)
//        );
//    }
//
//
//    ////PartyEnded 구독 진입점. 마지낙 XpGranted 리스너가 실패했어도 여기서 잡힘.
//    @Transactional(readOnly = true)
//    public void onPartyEnded(UUID partyId, Instant endedAt){
//        recalculate(partyId, endedAt);
//    }
//
//    ///파티의 at 이 속한주 xp 를 다시 계산에 덮어씀. 절대값이라 몇번불러도 같음.
//    ///ZADD는 트랜잭션 밖의 작업이지만, 다음 호출이 다시 덮어쓰므로 수렴함.
//    long recalculate(UUID partyId, Instant at){
//        PartyWeek week = PartyWeek.of(at);
//        long xp = memberRepository.sumWeeklyXp(partyId, week.start(), week.end());
//        rankingStore.save(week, partyId, xp);
//        log.debug("파티 랭킹갱신. partyId={} week={} weeklyXp={}", partyId, week.key(), xp);
//        return xp;
//    }
//
//    @Transactional(readOnly = true)
//    public PartyRankingView getRanking(UUID userId, int page, int size, String weekKey){
//
//
//        validatePaging(page, size);
//        PartyWeek week = weekKey == null ? PartyWeek.of(clock.instant()) :
//                PartyWeek.parse(weekKey);
//
//        long start = (long) page * size;
//        long end = start + size - 1;
//        List<ScoredParty> scored = rankingStore.findPage(week, start, end);
//        long total = rankingStore.size(week);
//
//        List<PartyRankingView.Entry> entries = scored.isEmpty() ? List.of() : toEntries(week, socred, start);
//
//        PartyRankingView.MyParty myParty = memberRepository.findActiveByUserId(userId)
//                .flatMap(m -> rankingStore.findScore(week, m.getPartyId())
//                        .map(xp -> new PartyRankingView.MyParty(rankingStore.countHigherThan(week, xp) + 1, m.getPartyId(), xp)))
//                .orElse(null);
//
//        return new PartyRankingView(week.key(), entries, myParty, page, size, total);
//    }
//
//
//    ////표준 경쟁 순위 1,2,3,4 개인 랭킹 RankingQueryService.assignRanks와 같은 알고리즘
//    ///
//    private List<PartyRankingView.Entry> toEntries(PartyWeek week, List<ScoredParty> scored, long start) {
//        Map<UUID, Party> parties = partyRepository.findAllByIds(
//                        scored.stream().map(ScoredParty::partyId).toList())
//                .stream().collect(Collectors.toMap(Party::getId, Function.identity()));
//
//        List<PartyRankingView.Entry> result = new ArrayList<>(scored.size());
//        long rank = rankingStore.countHigherThan(week, scored.get(0).weeklyXp()) + 1;
//        long prevScore = scored.get(0).weeklyXp();
//        long index = start;
//
//        for (ScoredParty s : scored) {
//            if (s.weeklyXp() != prevScore) {
//                rank = index + 1;
//                prevScore = s.weeklyXp();
//            }
//            Party p = parties.get(s.partyId());
//            // ZSET 에는 있는데 DB 에 없는 파티는 없어야 하지만, 있어도 조회가 깨지면 안 된다.
//            if (p == null) {
//                log.warn("ZSET 에는 있는데 DB 에 없는 파티. partyId={} week={}", s.partyId(), week.key());
//            }
//            String name = p == null ? "(삭제된 파티)" : p.getPartyName();
//            int members = p == null ? 0 : p.getCurrentMember();
//            result.add(new PartyRankingView.Entry(rank, s.partyId(), name, members, s.weeklyXp()));
//            index++;
//        }
//        return result;
//    }
//
//
//
//
//    private void validatePaging(int page, int size) {
//        if (page < 0) {
//            throw new IllegalArgumentException("page는 0이상이어야 합니다: " + page);
//        }
//        if (size < MIN_SIZE || size > MAX_SIZE) {
//            throw new IllegalArgumentException("size는 " + MIN_SIZE + " 이상 " + MAX_SIZE + " 이하여야 합니다: " + size);
//        }
//    }
//
//
//
//
//
//
//
//}
