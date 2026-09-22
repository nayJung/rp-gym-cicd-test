package com.workoutdone.rpgym.game.party.domain;

/** party_outbox_events.aggregate_type. (aggregate_type, aggregate_id, event_type) 유니크의 한 축이다. */
public enum PartyAggregateType {
    PARTY,
    PARTY_MEMBER,
    PARTY_INVITATION
}
