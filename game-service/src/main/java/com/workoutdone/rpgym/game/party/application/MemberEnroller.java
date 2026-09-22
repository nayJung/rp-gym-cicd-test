package com.workoutdone.rpgym.game.party.application;


import com.workoutdone.rpgym.game.party.domain.aggregate.PartyMember;
import com.workoutdone.rpgym.game.party.domain.repo.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

///////1인 1파티 최종 방어
////멤버 행 INSERT를 한 곳으로 모아서 생성, 수락, 매칭 세 경로가 전부 여기를 지나게 함
/// 사전 검사findActiveByUserId는 통과했는데 그 직후 다른요청이 먼저 들어온 경우,
/// uk_party_members_active_one_party 유니크가 insert를 거부. 그예외를 409로 바꿈.
///사전 검사는 에러 메시지용이고, 진짜 방어선은 이 유니크 인덱스다.
/// PartyMemberRepositoryImpl.save 가 saveAndFlush 라서 예외가 커밋이 아니라 여기서 난다.
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEnroller {

    private final PartyMemberRepository memberRepository;

    public void assertNotInParty(UUID userId) {
        memberRepository.findActiveByUserId(userId).ifPresent(m -> {
            log.debug("이미 파티 소속 — 409 로 응답. userId={} partyId={}", userId, m.getPartyId());
            throw new PartyException(PartyErrorCode.ALREADY_IN_PARTY);
        });
    }



    public PartyMember enroll(PartyMember member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            // 사전 검사와 INSERT 사이에 다른 요청이 먼저 들어온 경합. 정상이지만 자주 보이면 클라이언트 중복 호출을 의심한다.
            log.warn("1인 1파티 유니크 위반 — 409 로 응답. userId={} partyId={}", member.getUserId(), member.getPartyId());
            throw new PartyException(PartyErrorCode.ALREADY_IN_PARTY);
        }
    }



}
