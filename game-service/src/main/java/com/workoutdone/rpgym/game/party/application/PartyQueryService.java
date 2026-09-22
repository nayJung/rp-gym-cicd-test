package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.game.party.application.view.PartyView;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import com.workoutdone.rpgym.game.party.domain.repo.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

///내 파티

@Service
@RequiredArgsConstructor
public class PartyQueryService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository memberRepository;
    private final Clock clock;



    @Transactional(readOnly = true)
    public PartyView getMyParty(UUID userId) {
        PartyMember me = memberRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new PartyException(PartyErrorCode.NOT_IN_PARTY));

        Party party = partyRepository.findById(me.getPartyId())
                .orElseThrow(() -> new IllegalStateException("멤버는 있는데 파티가 없습니다: " + me.getPartyId()));
        List<PartyMember> members = memberRepository.findActiveByPartyId(party.getId());

        // displayStatus 가 lazy 판정을 한다. 배치가 늦어도 마감 지난 파티는 ACTIVE 로 나간다.
        return PartyView.of(party, members, clock.instant());
    }

}
