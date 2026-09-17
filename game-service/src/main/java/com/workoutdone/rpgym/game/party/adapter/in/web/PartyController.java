package com.workoutdone.rpgym.game.party.adapter.in.web;


import com.workoutdone.rpgym.game.party.adapter.in.web.dto.*;
import com.workoutdone.rpgym.game.party.application.PartyCommandService;
import com.workoutdone.rpgym.game.party.application.PartyInvitationService;
import com.workoutdone.rpgym.game.party.application.PartyMatchingService;
import com.workoutdone.rpgym.game.party.application.PartyQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


/**
 * 인증 주체는 게이트웨이가 넣어주는 X-User-Id 헤더 캐릭터컨트롤러와 동일
 * 초대 수락/거절/목록은 URL이 /parties/invitations로 시작해서 partyInvitationContoller에.
 *
 * 파티 랭킹은 ranking 도메인
 */


@RestController
@RequestMapping("/api/v1/games/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyCommandService commandService;
    private final PartyQueryService queryService;
    private final PartyInvitationService invitationService;
    private final PartyMatchingService matchingService;


    @PostMapping
    public ResponseEntity<PartyResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreatePartyRequest request
    ){
        PartyResponse body = PartyResponse.from(
                commandService.create(userId, request.partyName(), request.visibility())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/me")
    public ResponseEntity<PartyResponse> getMyParty(@RequestHeader("X-User-Id") UUID userId){
        return ResponseEntity.ok(PartyResponse.from(queryService.getMyParty(userId)));
    }


    ///부분 성공이라 전체 거절이 아닌 한 항상 200. 건별 결과는 본문에서.
    @PostMapping("/{partyId}/invitations")
    public ResponseEntity<InviteResponse> invite(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID partyId,
            @Valid @RequestBody InviteRequest request
    ){
        return ResponseEntity.ok(InviteResponse.from(
                invitationService.invite(userId, partyId, request.inviteeIds())
        ));
    }


    ///본문은 선택 (required = false)
    @PostMapping("/matching")
    public ResponseEntity<MatchingResponse> match(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody(required = false) MatchingRequest request
    ){
        String partyName = request == null ? null : request.partyName();
        return ResponseEntity.ok(MatchingResponse.from(matchingService.match(userId, partyName)));
    }

    @PostMapping("/{partyId}/start")
    public ResponseEntity<StartPartyResponse> start(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID partyId
    ){
        return ResponseEntity.ok(StartPartyResponse.from(commandService.start(userId, partyId)));
    }

    @DeleteMapping("/me/membership")
    public ResponseEntity<LeavePartyResponse> leave(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(LeavePartyResponse.from(commandService.leave(userId)));
    }








}

















