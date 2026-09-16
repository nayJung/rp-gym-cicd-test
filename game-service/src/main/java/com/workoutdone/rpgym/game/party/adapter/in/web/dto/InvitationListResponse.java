package com.workoutdone.rpgym.game.party.adapter.in.web.dto;

import com.workoutdone.rpgym.game.party.application.view.InvitationView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvitationListResponse(
        List<Item> invitations
) {
    public record Item(UUID invitationId, UUID partyId, String partyName, UUID inviterId,
                       int memberCount, int maxMember, Instant createdAt, Instant expiresAt){}


    public static InvitationListResponse from(
            List<InvitationView> views
    ){
        return new InvitationListResponse(views.stream()
                .map(v -> new Item(v.invitationId(),
                        v.partyId(), v.partyName(),
                        v.inviterId(), v.memberCount(),
                        v.maxMember(), v.createdAt(),
                        v.expiresAt()))
                .toList());
    }
}
