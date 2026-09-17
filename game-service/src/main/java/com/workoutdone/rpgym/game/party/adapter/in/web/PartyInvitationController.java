package com.workoutdone.rpgym.game.party.adapter.in.web;

import com.workoutdone.rpgym.game.party.adapter.in.web.dto.AcceptInvitationResponse;
import com.workoutdone.rpgym.game.party.adapter.in.web.dto.InvitationListResponse;
import com.workoutdone.rpgym.game.party.adapter.in.web.dto.RejectInvitationResponse;
import com.workoutdone.rpgym.game.party.application.PartyErrorCode;
import com.workoutdone.rpgym.game.party.application.PartyException;
import com.workoutdone.rpgym.game.party.application.PartyInvitationService;
import com.workoutdone.rpgym.game.party.application.view.AcceptOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/games/parties/invitations")
@RequiredArgsConstructor
public class PartyInvitationController {

    private final PartyInvitationService invitationService;

    @GetMapping
    public ResponseEntity<InvitationListResponse> myInvitations(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(InvitationListResponse.from(invitationService.findPending(userId)));
    }

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<AcceptInvitationResponse> accept(
            @RequestHeader("X-User-Id")UUID userId,
            @PathVariable UUID invitationId
            ){
        AcceptOutcome outcome = invitationService.accept(userId, invitationId);
        if (outcome instanceof AcceptOutcome.Full){
            throw new PartyException(PartyErrorCode.PARTY_FULL);
        }
        return ResponseEntity.ok(AcceptInvitationResponse.from((AcceptOutcome.Joined) outcome));
    }


    @PostMapping("/{invitationId}/reject")
    public ResponseEntity<RejectInvitationResponse> reject(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID invitationId
    ){
        return ResponseEntity.ok(RejectInvitationResponse.from(invitationService.reject(userId, invitationId)));
    }





}
