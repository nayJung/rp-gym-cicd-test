package com.workoutdone.rpgym.game.party.adapter.in.web;

import com.workoutdone.rpgym.common.exception.GlobalExceptionHandler;
import com.workoutdone.rpgym.game.exception.GameExceptionHandler;
import com.workoutdone.rpgym.game.party.application.PartyCommandService;
import com.workoutdone.rpgym.game.party.application.PartyErrorCode;
import com.workoutdone.rpgym.game.party.application.PartyException;
import com.workoutdone.rpgym.game.party.application.PartyInvitationService;
import com.workoutdone.rpgym.game.party.application.PartyMatchingService;
import com.workoutdone.rpgym.game.party.application.PartyQueryService;
import com.workoutdone.rpgym.game.party.application.view.InvitationResultView;
import com.workoutdone.rpgym.game.party.application.view.MatchingResultView;
import com.workoutdone.rpgym.game.party.application.view.PartyView;
import com.workoutdone.rpgym.game.party.domain.MemberRole;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartyController.class)
@Import({GameExceptionHandler.class, GlobalExceptionHandler.class})
class PartyControllerTest {

    private static final String URL = "/api/v1/games/parties";
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PartyCommandService commandService;
    @MockitoBean private PartyQueryService queryService;
    @MockitoBean private PartyInvitationService invitationService;
    @MockitoBean private PartyMatchingService matchingService;

    private static PartyView view(UUID owner, PartyMetric metric) {
        return new PartyView(
                UUID.randomUUID(), "아침 습관 챌린지", owner, PartyStatus.RECRUITING, PartyVisibility.PRIVATE, metric,
                1, 4, NOW.plusSeconds(86400), NOW.plusSeconds(7 * 86400), NOW,
                List.of(new PartyView.MemberView(owner, MemberRole.OWNER, NOW)));
    }

    // ───────────── 생성 ─────────────

    @DisplayName("파티 생성은 201 이고 RECRUITING · metric 이 응답에 실린다")
    @Test
    void create() throws Exception {
        UUID userId = UUID.randomUUID();
        given(commandService.create(eq(userId), anyString(), any(), eq(PartyMetric.STEPS)))
                .willReturn(view(userId, PartyMetric.STEPS));

        mockMvc.perform(post(URL)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyName\":\"아침 습관 챌린지\",\"metric\":\"STEPS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.metric").value("STEPS"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @DisplayName("파티 이름이 비어 있으면 400 INVALID_INPUT 과 fields 가 내려온다")
    @Test
    void createWithBlankName() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyName\":\"   \",\"metric\":\"STEPS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields[0].field").value("partyName"));
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    @DisplayName("metric 이 없으면 400 INVALID_INPUT — 파티 퀘스트가 볼 지표는 필수다")
    @Test
    void createWithoutMetric() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyName\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields[0].field").value("metric"));
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    @DisplayName("metric 이 삼종 밖 문자열이면 역직렬화에서 400 이 난다")
    @Test
    void createWithUnknownMetric() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyName\":\"x\",\"metric\":\"STEP\"}"))
                .andExpect(status().isBadRequest());
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    @DisplayName("이미 파티가 있으면 409 ALREADY_IN_PARTY")
    @Test
    void createWhenAlreadyInParty() throws Exception {
        given(commandService.create(any(), anyString(), any(), any()))
                .willThrow(new PartyException(PartyErrorCode.ALREADY_IN_PARTY));

        mockMvc.perform(post(URL)
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyName\":\"x\",\"metric\":\"ACTIVE_MINUTES\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_PARTY"));
    }

    // ───────────── 조회 ─────────────

    @DisplayName("X-User-Id 가 없으면 401")
    @Test
    void missingHeader() throws Exception {
        mockMvc.perform(get(URL + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @DisplayName("소속 파티가 없으면 404 NOT_IN_PARTY")
    @Test
    void myPartyNotFound() throws Exception {
        given(queryService.getMyParty(any())).willThrow(new PartyException(PartyErrorCode.NOT_IN_PARTY));

        mockMvc.perform(get(URL + "/me").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_IN_PARTY"));
    }

    // ───────────── 매칭 ─────────────

    @DisplayName("자동 매칭은 metric 을 서비스로 그대로 넘기고 결과에 metric 이 실린다")
    @Test
    void matching() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        given(matchingService.match(eq(userId), any(), eq(PartyMetric.ACTIVE_CALORIES)))
                .willReturn(new MatchingResultView(MatchingResultView.Result.MATCHED, partyId, "p",
                        PartyMetric.ACTIVE_CALORIES, PartyStatus.RECRUITING, 3, 4, NOW.plusSeconds(3600)));

        mockMvc.perform(post(URL + "/matching")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metric\":\"ACTIVE_CALORIES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("MATCHED"))
                .andExpect(jsonPath("$.partyId").value(partyId.toString()))
                .andExpect(jsonPath("$.metric").value("ACTIVE_CALORIES"))
                .andExpect(jsonPath("$.memberCount").value(3));
    }

    @DisplayName("자동 매칭에 metric 이 없으면 400")
    @Test
    void matchingWithoutMetric() throws Exception {
        mockMvc.perform(post(URL + "/matching")
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verify(matchingService, never()).match(any(), any(), any());
    }

    // ───────────── 초대 ─────────────

    @DisplayName("초대 결과는 사람마다 CREATED / SELF_INVITE 로 갈라져 내려온다")
    @Test
    void invite() throws Exception {
        UUID inviter = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        given(invitationService.invite(eq(inviter), eq(partyId), any()))
                .willReturn(List.of(
                        InvitationResultView.skipped(inviter, InvitationResultView.Result.SELF_INVITE),
                        InvitationResultView.created(invitee, invitationId, NOW.plusSeconds(86400))));

        mockMvc.perform(post(URL + "/" + partyId + "/invitations")
                        .header("X-User-Id", inviter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"" + inviter + "\",\"" + invitee + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].result").value("SELF_INVITE"))
                .andExpect(jsonPath("$.results[0].invitationId").doesNotExist())
                .andExpect(jsonPath("$.results[1].result").value("CREATED"))
                .andExpect(jsonPath("$.results[1].invitationId").value(invitationId.toString()));
    }

    @DisplayName("inviteeIds 가 비어 있으면 400 INVALID_INPUT (List 라 @NotEmpty)")
    @Test
    void inviteWithEmptyList() throws Exception {
        mockMvc.perform(post(URL + "/" + UUID.randomUUID() + "/invitations")
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields[0].field").value("inviteeIds"));
    }

    @DisplayName("정원이 넘치면 409 PARTY_FULL")
    @Test
    void inviteOverCapacity() throws Exception {
        given(invitationService.invite(any(), any(), any()))
                .willThrow(new PartyException(PartyErrorCode.PARTY_FULL));

        mockMvc.perform(post(URL + "/" + UUID.randomUUID() + "/invitations")
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTY_FULL"));
    }
}
