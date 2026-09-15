package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PartyJpaRepository extends JpaRepository<Party, UUID> {


    ////
    @Query("SELECT p FROM Party p WHERE p.id = :id")
    Optional<Party> findByIdForUpdate(@Param("id") UUID id);


    //// 정원확보. 검증(status · deadline · 빈자리)과 증가가 한 문장이라 읽기-쓰기 틈이 없다.
    int reserveSeat(@Param("partyId") UUID partyId,
                    @Param("recruiting")PartyStatus recruiting,
                    @Param("now") Instant now);






}
