package com.workoutdone.rpgym.game.party.adapter.in.web;

import com.workoutdone.rpgym.common.exception.GlobalExceptionHandler;
import com.workoutdone.rpgym.game.exception.GameExceptionHandler;
import com.workoutdone.rpgym.game.party.application.PartyErrorCode;
import com.workoutdone.rpgym.game.party.application.PartyException;
import com.workoutdone.rpgym.game.party.application.PartyInvitationService;
import com.workoutdone.rpgym.game.party.application.view.AcceptOutcome;
import com.workoutdone.rpgym.game.party.application.view.InvitationView;
import com.workoutdone.rpgym.game.party.application.view.RejectResultView;
import com.workoutdone.rpgym.game.party.domain.InvitationStatus;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification 이 Slack 버튼에서 호출할 계약. 여기 나오는 상태 코드 · code 값이 곧 알림 쪽에 넘기는 표다.
 */
@WebMvcTest(PartyInvitationController.class)
@Import({GameExceptionHandler.class, GlobalExceptionHandler.class})
class PartyInvitationControllerTest {

    private static final String URL = "/api/v1/games/parties/invitations";
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PartyInvitationService invitationService;

    // ───────────── 수락 ─────────────

    @DisplayName("수락 성공: 200, ACCEPTED, 파티 상태 · 인원이 실린다")
    @Test
    void accept() throws Exception {
        UUID invitee = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        given(invitationService.accept(eq(invitee), eq(invitationId)))
                .willReturn(new AcceptOutcome.Joined(invitationId, InvitationStatus.ACCEPTED, partyId,
                        PartyStatus.RECRUITING, 2, 4, NOW));

        mockMvc.perform(post(URL + "/" + invitationId + "/accept").header("X-User-Id", invitee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.partyId").value(partyId.toString()))
                .andExpect(jsonPath("$.partyStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.maxMember").value(4));
    }

    @DisplayName("수락으로 4/4 가 되면 partyStatus 가 ACTIVE 로 내려온다 — 알림은 이걸로 '파티 시작' 을 안다")
    @Test
    void acceptFillsParty() throws Exception {
        UUID invitee = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        given(invitationService.accept(eq(invitee), eq(invitationId)))
                .willReturn(new AcceptOutcome.Joined(invitationId, InvitationStatus.ACCEPTED, UUID.randomUUID(),
                        PartyStatus.ACTIVE, 4, 4, NOW));

        mockMvc.perform(post(URL + "/" + invitationId + "/accept").header("X-User-Id", invitee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.memberCount").value(4));
    }

    @DisplayName("자리가 없으면 409 PARTY_FULL — 서비스는 Full 을 돌려주고(초대 CANCELED 저장) 컨트롤러가 409 로 바꾼다")
    @Test
    void acceptWhenFull() throws Exception {
        UUID invitationId = UUID.randomUUID();
        given(invitationService.accept(org.mockito.ArgumentMatchers.any(), eq(invitationId)))
                .willReturn(new AcceptOutcome.Full(invitationId));

        mockMvc.perform(post(URL + "/" + invitationId + "/accept").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTY_FULL"));
    }

    @DisplayName("만료됐거나 이미 처리된 초대는 409 INVITATION_NOT_PENDING")
    @Test
    void acceptNotPending() throws Exception {
        UUID invitationId = UUID.randomUUID();
        given(invitationService.accept(org.mockito.ArgumentMatchers.any(), eq(invitationId)))
                .willThrow(new PartyException(PartyErrorCode.INVITATION_NOT_PENDING));

        mockMvc.perform(post(URL + "/" + invitationId + "/accept").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
    }

    @DisplayName("초대받은 사람이 아니면 403 NOT_INVITEE — Slack userId 매핑이 틀렸을 때 나오는 코드")
    @Test
    void acceptByStranger() throws Exception {
        UUID invitationId = UUID.randomUUID();
        given(invitationService.accept(org.mockito.ArgumentMatchers.any(), eq(invitationId)))
                .willThrow(new PartyException(PartyErrorCode.NOT_INVITEE));

        mockMvc.perform(post(URL + "/" + invitationId + "/accept").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_INVITEE"));
    }

    @DisplayName("없는 초대는 404 INVITATION_NOT_FOUND")
    @Test
    void acceptNotFound() throws Exception {
        UUID invitationId = UUID.randomUUID();
        given(invitationService.accept(org.mockito.ArgumentMatchers.any(), eq(invitationId)))
                .willThrow(new PartyException(PartyErrorCode.INVITATION_NOT_FOUND));

        mockMvc.perform(post(URL + "/" + invitationId + "/accept").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
    }

    // ───────────── 거절 ─────────────

    @DisplayName("거절 성공: 200, REJECTED, respondedAt")
    @Test
    void reject() throws Exception {
        UUID invitee = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        given(invitationService.reject(eq(invitee), eq(invitationId)))
                .willReturn(new RejectResultView(invitationId, InvitationStatus.REJECTED, NOW));

        mockMvc.perform(post(URL + "/" + invitationId + "/reject").header("X-User-Id", invitee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.invitationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.respondedAt").value(NOW.toString()));
    }

    @DisplayName("거절: 이미 처리된 초대는 409 INVITATION_NOT_PENDING")
    @Test
    void rejectNotPending() throws Exception {
        UUID invitationId = UUID.randomUUID();
        given(invitationService.reject(org.mockito.ArgumentMatchers.any(), eq(invitationId)))
                .willThrow(new PartyException(PartyErrorCode.INVITATION_NOT_PENDING));

        mockMvc.perform(post(URL + "/" + invitationId + "/reject").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
    }

    // ───────────── 목록 · 공통 ─────────────

    @DisplayName("받은 초대 목록: 파티 이름 · 인원 · 만료 시각이 실린다")
    @Test
    void myInvitations() throws Exception {
        UUID invitee = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        given(invitationService.findPending(eq(invitee))).willReturn(List.of(
                new InvitationView(invitationId, UUID.randomUUID(), "아침 걷기", UUID.randomUUID(),
                        2, 4, NOW, NOW.plusSeconds(86400))));

        mockMvc.perform(get(URL).header("X-User-Id", invitee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations[0].invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.invitations[0].partyName").value("아침 걷기"))
                .andExpect(jsonPath("$.invitations[0].memberCount").value(2));
    }

    @DisplayName("X-User-Id 가 없으면 401")
    @Test
    void missingHeader() throws Exception {
        mockMvc.perform(post(URL + "/" + UUID.randomUUID() + "/accept"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
