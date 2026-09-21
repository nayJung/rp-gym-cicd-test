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

// ── 초대 담당(알림) · 앱 담당에게 ─────────────────────────────────────────
// 초대 수락 · 거절의 입구다. 슬랙 버튼에서 오든 앱에서 오든 결국 여기로 들어온다.
//
//   GET  /api/v1/games/parties/invitations                  내 PENDING 초대 목록
//   POST /api/v1/games/parties/invitations/{id}/accept      수락
//   POST /api/v1/games/parties/invitations/{id}/reject      거절
//
// 인증 주체는 게이트웨이가 넣어주는 X-User-Id 헤더다. 다른 API 와 같다.
// {id} 는 PARTY_INVITED 이벤트의 data.invitationId 를 그대로 쓰면 된다.
//
// 두 번 눌러도 안전하다. 이미 수락된 초대에 수락이 또 오면 같은 응답을 돌려준다(멱등).
// 그 사이 자리가 찼으면 409 PARTY_FULL 이고, 그때 초대는 CANCELED 로 닫히면서
// PARTY_INVITATION_CLOSED 가 reason=PARTY_FULL 로 나간다 — 결과를 알리는 건 그 이벤트다.
//
// 결과 통지를 응답으로만 받지 말 것. 만료 · 파티 마감 · 해산처럼 유저가 누르지 않았는데
// 닫히는 경로가 있고, 그쪽은 이벤트로만 알 수 있다. 초대 1건당 CLOSED 는 정확히 1번 나간다.
// 상세: https://github.com/workout-done/rp-gym/issues/122
// ──────────────────────────────────────────────────────────────────────
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
