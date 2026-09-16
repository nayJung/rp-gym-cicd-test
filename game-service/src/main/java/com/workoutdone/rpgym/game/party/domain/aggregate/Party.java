package com.workoutdone.rpgym.game.party.domain.aggregate;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedEntity;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "parties", schema = "game_service")
public class Party extends BaseCreatedUpdatedEntity {
    /**
     * 파티 엔티티 클래스
     * current_member는 파생값. 엔티티의 +1 은 절대 쓰지않음. - 정원 확보는 항상
     * PartyRepository.reserveSeat()의 조건부 UPDATE 한 문장으로 함.
     * 이 엔티티는 감소(탈퇴)와 상태전이만 존재.
     * 탈퇴는 파티 행을 비관적 락으로 잡고 하므로 안점함.
     *
     */

    public static final int NAME_MAX_LENGTH = 50;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "party_name", nullable = false, length = NAME_MAX_LENGTH)
    private String partyName;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PartyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10, updatable = false)
    private PartyVisibility visibility;

    @Column(name = "max_member", nullable = false, updatable = false)
    private int maxMember;

    @Column(name = "current_member", nullable = false)
    private int currentMember;

    @Column(name = "matching_deadline_at", nullable = false)
    private Instant matchingDeadlineAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;


    public static Party create(
            UUID id,
            String partyName,
            UUID ownerId,
            PartyVisibility visibility,
            int maxMember,
            Instant now,
            Duration recruitDuration,
            Duration lifetime
    ){
        String name = partyName == null ? "" : partyName.strip();
        if (name.isEmpty()){
            throw new IllegalArgumentException("파티 이름은 비어있을수 없습니다.");

        }
        if (name.length() > NAME_MAX_LENGTH){
            throw new IllegalArgumentException("파티 이름은 최대 " + NAME_MAX_LENGTH + "자입니다.");
        }
        if (maxMember < 1){
            throw new IllegalArgumentException("정원은 1 이상이어야 합니다: " + maxMember);
        }

        Party party = new Party();
        party.id = id;
        party.partyName = name;
        party.ownerId = ownerId;
        party.status = PartyStatus.RECRUITING;
        party.visibility = visibility;
        party.maxMember = maxMember;
        party.currentMember = 1; // 생성자 본인
        party.matchingDeadlineAt = now.plus(recruitDuration);
        party.endsAt = now.plus(lifetime); // deadline 이 아니라 생성 기준. 직접/매칭 수명이 같아짐.

        return party;
    }

    /**
     * 모든 필드를 받는 복원용 팩토리. 프로덕션 경로에서는 쓰지 않는다 —
     * 서비스 단위 테스트에서 "이미 3명인 파티" 같은 상태를 만들 때만 쓴다 (currentMember 를 올리는 유일한 길).
     */
    public static Party restore(
            UUID id, String partyName, UUID ownerId, PartyStatus status,
            PartyVisibility visibility, int maxMember, int currentMember,
            Instant matchingDeadlineAt, Instant endsAt
    ){
        Party party = new Party();
        party.id = id;
        party.partyName = partyName;
        party.ownerId = ownerId;
        party.status = status;
        party.visibility = visibility;
        party.maxMember = maxMember;
        party.currentMember = currentMember;
        party.matchingDeadlineAt = matchingDeadlineAt;
        party.endsAt = endsAt;
        return party;
    }

    public boolean isFull(){
        return currentMember >= maxMember;
    }

    /** DB 상태가 RECRUITING 이어도 마감 시각이 지났으면 모집중이 아니다. Lazy 판정*/
    public boolean isRecruiting(Instant now){
        return status == PartyStatus.RECRUITING && now.isBefore(matchingDeadlineAt);
    }

    /** 배치가 늦어도 응답은 정확해야 한다. 조회용 상태 */
    public PartyStatus displayStatus(Instant now){
        if (status == PartyStatus.RECRUITING && !now.isBefore(matchingDeadlineAt)){
            return PartyStatus.ACTIVE;
        }
        return status;
    }

    public boolean isOwner(UUID userId){
        return ownerId.equals(userId);
    }

    public void memberLeft(){
        if (currentMember <= 0){
            throw new IllegalArgumentException("인원이 이미 0입니다: " + id);
        }
        currentMember--;
        if (currentMember == 0){
            status = PartyStatus.DISBANDED;
        }
    }

    public void changeOwner(UUID newOwnerId){
        this.ownerId = newOwnerId;
    }

    /** 수명 만료. ACTIVE에서만 ENDED로 간다.  RECRUITING 인채로 ends_at이 오는 일은 없음. (deadline < ends_at) */
    public void end(){
        if (status != PartyStatus.ACTIVE){
            throw new IllegalArgumentException("ACTIVE 파티만 종료할수있습니다: " + id + " " + status);
        }
        status = PartyStatus.ENDED;
    }

}
