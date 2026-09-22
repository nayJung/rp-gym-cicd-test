package com.workoutdone.rpgym.game.party.application;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

//파티 정책값. 코드에 숫자를 박지않고 여기로 모음.
//maxMember : 정원. 현재 4 고정
// recruitDuration : 모집 시간. 생성시각 + 이 값 = matching_deadline_at
// lifetime : 파티 수명. 생성시각 + 이 값 = ends_at
// invitationTtl : 초대 만료. 초대 시각 + 이 값 = expires_at.
//recruitDuration 이하여야 초대가 파티보다 먼저죽지 않음.
// matchingCandidates 자동 매칭 시 한 번에 살펴볼 후보 파티 수
@ConfigurationProperties(prefix = "rpgym.party")
public record PartyProperties(
        int maxMember,
        Duration recruitDuration,
        Duration lifetime,
        Duration invitationTtl,
        int matchingCandidates
) {
    public PartyProperties {
        if (maxMember < 1){
            throw new IllegalArgumentException("rpgym.party.max-member must be >= 1");
        }
        if (invitationTtl.compareTo(recruitDuration) > 0){
            throw new IllegalArgumentException("rpgym.party.invitation-ttl must be <= recruit-duration(초대가 파티 마감보다 오래살면 안된다.");
        }
    }

}
