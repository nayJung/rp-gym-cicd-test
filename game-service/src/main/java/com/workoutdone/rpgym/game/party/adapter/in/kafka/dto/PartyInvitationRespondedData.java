package com.workoutdone.rpgym.game.party.adapter.in.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * 유저가 슬랙 초대 버튼을 누른 결과. quest 의 QuestOfferResponded 와 같은 모양이다
 * (대상 id + outcome). outcome 은 ACCEPTED / REJECTED.
 *
 * outcome 을 enum 이 아니라 String 으로 받는다 — 모르는 값이 와도 역직렬화가 아니라
 * 컨슈머의 분기에서 걸러야 파티션이 안 막힌다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartyInvitationRespondedData(
        UUID invitationId,
        String outcome
) {
}
