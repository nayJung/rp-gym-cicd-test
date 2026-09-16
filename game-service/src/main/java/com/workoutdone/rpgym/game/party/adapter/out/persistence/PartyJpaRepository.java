package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyJpaRepository extends JpaRepository<Party, UUID> {


    ////
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Party p WHERE p.id = :id")
    Optional<Party> findByIdForUpdate(@Param("id") UUID id);


    //// 정원확보. 검증(status · deadline · 빈자리)과 증가가 한 문장이라 읽기-쓰기 틈이 없다.
    /// 락은 이 statement 순간만 잡힘
    /// clearAutomatically: 이 UPDATE는 영속성 컨텍스트를 거치지 않음 이후 같은 트랜잭션에서
    /// findById로 읽는 Party가 옜 current_member 를 들고있지않도록 1차캐시를 비움.
    /// flushAutomatically: 그 전에 쌓인 변경(있다면)을 먼저 DB에 반영한다.

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
               update Party p
               SET p.currentMember = p.currentMember + 1 
               where p.id = :partyId
               and p.status = :reqruiting
               and p.matchingDeadlineAt > :now
               and p.currentMember < p.maxMember
"""
    )
    int reserveSeat(@Param("partyId") UUID partyId,
                    @Param("recruiting")PartyStatus recruiting,
                    @Param("now") Instant now);



    ////RECRUITING → ACTIVE. 한 번만 성공한다. deadline 을 now 로 당겨 "언제 닫혔나" 를 남긴다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Party p
               SET p.status = :active, p.matchingDeadlineAt = :now
             where p.id = :partyId
               and p.status = :recruiting
            """)
    int closeRecruiting(@Param("partyId") UUID partyId,
                        @Param("recruiting") PartyStatus recruiting,
                        @Param("active") PartyStatus active,
                        @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Party p
               SET p.status = :active, p.matchingDeadlineAt = :now
             WHERE p.id = :partyId
               AND p.ownerId = :ownerId
               AND p.status = :recruiting
            """)
    int closeRecruitingByOwner(@Param("partyId") UUID partyId,
                               @Param("ownerId") UUID ownerId,
                               @Param("recruiting") PartyStatus recruiting,
                               @Param("active") PartyStatus active,
                               @Param("now") Instant now);

    /////거의 찬 파티부터, 같으면 오래된 파티부터. idx_parties_matching 을 탄다.
    @Query("""
                select p from Party p
                where p.visibility = :visibility
                and p.status = :status
                and p.currentMember < p.maxMember
                and p.matchingDeadlineAt > :now
                order by  p.currentMember desc, p.createdAt asc
""")
    List<Party> findMatchingCandidates(@Param("visibility")PartyVisibility visibility,
                                       @Param("status") PartyStatus status,
                                       @Param("now") Instant now,
                                       Pageable pageable);


    @Query("""
            select p from Party p
            where p.visibility = :visibility
            and p.status = :status
            and p.currentMember < p.maxMember
            and p.matchingDeadlineAt > :now
            order by p.currentMember desc, p.createdAt asc
""")
    List<Party> findByStatusAndDeadlineBefore(@Param("status") PartyStatus status,
                                              @Param("now") Instant now,
                                              Pageable pageable);


    @Query("SELECT p FROM Party p WHERE p.status = :status AND p.endsAt <= :now ORDER BY p.endsAt ASC")
    List<Party> findByStatusAndEndsBefore(@Param("status") PartyStatus status,
                                              @Param("now") Instant now,
                                              Pageable pageable);

}
