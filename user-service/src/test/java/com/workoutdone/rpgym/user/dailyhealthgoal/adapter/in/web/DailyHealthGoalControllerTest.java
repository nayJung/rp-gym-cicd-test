package com.workoutdone.rpgym.user.dailyhealthgoal.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.dailyhealthgoal.adapter.in.web.dto.ReqRegisterDailyHealthGoalDto;
import com.workoutdone.rpgym.user.dailyhealthgoal.application.RegisterDailyHealthGoalCommand;
import com.workoutdone.rpgym.user.dailyhealthgoal.application.RegisterDailyHealthGoalResult;
import com.workoutdone.rpgym.user.dailyhealthgoal.application.RegisterDailyHealthGoalService;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyHealthGoalController.class)
class DailyHealthGoalControllerTest {

    private static final String REGISTER_URL = "/api/v1/daily-goals/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegisterDailyHealthGoalService registerDailyHealthGoalService;

    private ReqRegisterDailyHealthGoalDto validRequest() {
        return ReqRegisterDailyHealthGoalDto.builder()
                .stepGoal(5000)
                .activeMinutesGoal(60)
                .activeCaloriesGoal(500)
                .build();
    }

    @Test
    @DisplayName("USER role이고 정상 요청이면 201과 함께 일일 목표 정보를 반환한다")
    void registerDailyHealthGoal_success() throws Exception {
        RegisterDailyHealthGoalResult result = RegisterDailyHealthGoalResult.builder()
                .id(UUID.randomUUID())
                .stepGoal(5000)
                .activeMinutesGoal(60)
                .activeCaloriesGoal(500)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        given(registerDailyHealthGoalService.registerDailyHealthGoal(any())).willReturn(result);

        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stepGoal").value(5000))
                .andExpect(jsonPath("$.activeMinutesGoal").value(60))
                .andExpect(jsonPath("$.activeCaloriesGoal").value(500));
    }

    @Test
    @DisplayName("필드를 생략하면 해당 값이 null인 채로 Command에 전달된다")
    void registerDailyHealthGoal_omittedFieldsPassedAsNullToCommand() throws Exception {
        RegisterDailyHealthGoalResult result = RegisterDailyHealthGoalResult.builder()
                .id(UUID.randomUUID())
                .stepGoal(3000)
                .activeMinutesGoal(30)
                .activeCaloriesGoal(300)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        given(registerDailyHealthGoalService.registerDailyHealthGoal(any())).willReturn(result);

        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterDailyHealthGoalCommand> captor = ArgumentCaptor.forClass(RegisterDailyHealthGoalCommand.class);
        verify(registerDailyHealthGoalService).registerDailyHealthGoal(captor.capture());
        RegisterDailyHealthGoalCommand command = captor.getValue();

        assertThat(command.getStepGoal()).isNull();
        assertThat(command.getActiveMinutesGoal()).isNull();
        assertThat(command.getActiveCaloriesGoal()).isNull();
    }

    @Test
    @DisplayName("X-User-Role이 ADMIN이면 403 FORBIDDEN을 반환한다")
    void registerDailyHealthGoal_adminForbidden() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("X-User-Role이 USER가 아니면 403 FORBIDDEN을 반환한다")
    void registerDailyHealthGoal_disallowedRole() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("X-User-Id/X-User-Role 헤더가 둘 다 없으면 401 UNAUTHORIZED를 반환한다")
    void registerDailyHealthGoal_noHeaders() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("X-User-Role 헤더만 없으면 401 UNAUTHORIZED를 반환한다")
    void registerDailyHealthGoal_missingRoleHeader() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("stepGoal이 음수면 400 INVALID_INPUT을 반환한다")
    void registerDailyHealthGoal_negativeStepGoal() throws Exception {
        ReqRegisterDailyHealthGoalDto request = ReqRegisterDailyHealthGoalDto.builder()
                .stepGoal(-1)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("activeMinutesGoal이 음수면 400 INVALID_INPUT을 반환한다")
    void registerDailyHealthGoal_negativeActiveMinutesGoal() throws Exception {
        ReqRegisterDailyHealthGoalDto request = ReqRegisterDailyHealthGoalDto.builder()
                .activeMinutesGoal(-1)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("activeCaloriesGoal이 음수면 400 INVALID_INPUT을 반환한다")
    void registerDailyHealthGoal_negativeActiveCaloriesGoal() throws Exception {
        ReqRegisterDailyHealthGoalDto request = ReqRegisterDailyHealthGoalDto.builder()
                .activeCaloriesGoal(-1)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("이미 등록된 일일 목표가 있으면 409 DAILY_GOAL_ALREADY_EXISTS를 반환한다")
    void registerDailyHealthGoal_alreadyExists() throws Exception {
        given(registerDailyHealthGoalService.registerDailyHealthGoal(any()))
                .willThrow(new BaseException(DailyHealthGoalErrorCode.DAILY_GOAL_ALREADY_EXISTS));

        mockMvc.perform(post(REGISTER_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_GOAL_ALREADY_EXISTS"));
    }
}
