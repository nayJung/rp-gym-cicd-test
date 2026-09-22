package com.workoutdone.rpgym.game.quest.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.common.exception.GlobalExceptionHandler;
import com.workoutdone.rpgym.game.exception.GameExceptionHandler;
import com.workoutdone.rpgym.game.quest.adapter.in.web.dto.CreatePartyQuestRequest;
import com.workoutdone.rpgym.game.quest.application.PartyQuestCreateService;
import com.workoutdone.rpgym.game.quest.application.PartyQuestCreation;
import com.workoutdone.rpgym.game.quest.application.PartyQuestView;
import com.workoutdone.rpgym.game.quest.domain.Metric;
import com.workoutdone.rpgym.game.quest.domain.QuestStatus;

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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartyQuestController.class)
@Import({GameExceptionHandler.class, GlobalExceptionHandler.class})
class PartyQuestControllerTest {

    private static final String URL = "/api/v1/games/party-quests";
    private static final UUID PARTY_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PartyQuestCreateService partyQuestCreateService;

    private String body() throws Exception {
        return objectMapper.writeValueAsString(new CreatePartyQuestRequest(
                PARTY_ID, "퇴근길 함께 4000보", "STEPS", 4000,
                List.of(OWNER, UUID.randomUUID())));
    }

    private static PartyQuestView view() {
        return new PartyQuestView(
                UUID.randomUUID(), PARTY_ID, "퇴근길 함께 4000보", Metric.STEPS,
                4000, 0, QuestStatus.ACTIVE, 30,
                Instant.parse("2026-09-21T02:00:00Z"), Instant.parse("2026-09-21T14:59:59Z"),
                List.of(new PartyQuestView.MemberView(OWNER, 3000, 0)));
    }

    private void givenFails(PartyQuestCreation.Reason reason) {
        given(partyQuestCreateService.create(any()))
                .willReturn(new PartyQuestCreation.Failed(reason));
    }

    @Test
    @DisplayName("만들어지면 201과 파티 퀘스트를 돌려준다")
    void 생성되면_201이다() throws Exception {
        given(partyQuestCreateService.create(any()))
                .willReturn(new PartyQuestCreation.Created(view()));

        mockMvc.perform(post(URL)
                        .header("X-User-Id", OWNER.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("퇴근길 함께 4000보"))
                .andExpect(jsonPath("$.metric").value("STEPS"))
                .andExpect(jsonPath("$.targetValue").value(4000))
                .andExpect(jsonPath("$.currentValue").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rewardXp").value(30))
                .andExpect(jsonPath("$.members[0].baselineValue").value(3000));
    }

    @Test
    @DisplayName("인원이 올바르지 않으면 400이다")
    void 인원이_틀리면_400이다() throws Exception {
        givenFails(PartyQuestCreation.Reason.INVALID_MEMBERS);

        mockMvc.perform(post(URL)
                        .header("X-User-Id", OWNER.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_MEMBERS"));
    }

    @Test
    @DisplayName("명단에 없는 사람이 요청하면 403이다")
    void 남의_파티면_403이다() throws Exception {
        givenFails(PartyQuestCreation.Reason.NOT_A_MEMBER);

        mockMvc.perform(post(URL)
                        .header("X-User-Id", OWNER.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_PARTY_MEMBER"));
    }

    @Test
    @DisplayName("이미 진행 중이면 409다")
    void 이미_진행_중이면_409다() throws Exception {
        givenFails(PartyQuestCreation.Reason.PARTY_QUEST_ALREADY_ACTIVE);

        mockMvc.perform(post(URL)
                        .header("X-User-Id", OWNER.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTY_QUEST_ALREADY_ACTIVE"));
    }

    @Test
    @DisplayName("자정이 임박하면 409다 — 요청은 올바르고 지금이 아닐 뿐이다")
    void 자정_직전이면_409다() throws Exception {
        givenFails(PartyQuestCreation.Reason.TOO_LATE_IN_DAY);

        mockMvc.perform(post(URL)
                        .header("X-User-Id", OWNER.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_LATE_IN_DAY"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 401이다")
    void 헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
