package com.workoutdone.rpgym.user.user.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.common.security.UserRole;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ReqLoginDto;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ReqSignUpDto;
import com.workoutdone.rpgym.user.user.application.GetMyAccountResult;
import com.workoutdone.rpgym.user.user.application.GetMyAccountService;
import com.workoutdone.rpgym.user.user.application.LoginResult;
import com.workoutdone.rpgym.user.user.application.LoginService;
import com.workoutdone.rpgym.user.user.application.SignUpCommand;
import com.workoutdone.rpgym.user.user.application.SignUpResult;
import com.workoutdone.rpgym.user.user.application.SignUpService;
import com.workoutdone.rpgym.user.user.application.UpdateMyAccountService;
import com.workoutdone.rpgym.user.user.domain.UserStatus;
import com.workoutdone.rpgym.user.user.domain.UserErrorCode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final String SIGNUP_URL = "/api/v1/users/signup";
    private static final String LOGIN_URL = "/api/v1/users/login";
    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SignUpService signUpService;

    @MockitoBean
    private LoginService loginService;

    // UserController가 GetMyAccountService/UpdateMyAccountService에도 의존하므로, 컨텍스트 로딩을 위해 Mock으로 등록해야 한다.
    @MockitoBean
    private GetMyAccountService getMyAccountService;

    @MockitoBean
    private UpdateMyAccountService updateMyAccountService;

    private ReqSignUpDto validRequest() {
        return ReqSignUpDto.builder()
                .email("healthuser@example.com")
                .password("myPassw0rd!")
                .nickname("헬스퀘스트유저")
                .slackId("U0123ABC456")
                .build();
    }

    @Test
    @DisplayName("정상 요청이면 201과 함께 회원 정보를 반환한다")
    void signUp_success() throws Exception {
        SignUpResult result = SignUpResult.builder()
                .id(UUID.randomUUID())
                .email("healthuser@example.com")
                .nickname("헬스퀘스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        given(signUpService.signUp(any())).willReturn(result);

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("healthuser@example.com"))
                .andExpect(jsonPath("$.nickname").value("헬스퀘스트유저"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("이메일/닉네임/슬랙 아이디의 앞뒤 공백은 트리밍되고, 비밀번호는 트리밍되지 않은 채로 서비스에 전달된다")
    void signUp_trimsWhitespaceExceptPassword() throws Exception {
        SignUpResult result = SignUpResult.builder()
                .id(UUID.randomUUID())
                .email("healthuser@example.com")
                .nickname("헬스퀘스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        given(signUpService.signUp(any())).willReturn(result);

        String rawJson = """
                {
                  "email": "  healthuser@example.com  ",
                  "password": "  myPassw0rd!  ",
                  "nickname": "  헬스퀘스트유저  ",
                  "slackId": "  U0123ABC456  "
                }
                """;

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isCreated());

        ArgumentCaptor<SignUpCommand> captor = ArgumentCaptor.forClass(SignUpCommand.class);
        verify(signUpService).signUp(captor.capture());
        SignUpCommand command = captor.getValue();

        assertThat(command.getEmail()).isEqualTo("healthuser@example.com");
        assertThat(command.getNickname()).isEqualTo("헬스퀘스트유저");
        assertThat(command.getSlackId()).isEqualTo("U0123ABC456");
        assertThat(command.getRawPassword()).isEqualTo("  myPassw0rd!  ");
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400 INVALID_INPUT을 반환한다")
    void signUp_invalidEmail() throws Exception {
        ReqSignUpDto request = ReqSignUpDto.builder()
                .email("not-an-email")
                .password("myPassw0rd!")
                .nickname("닉네임")
                .slackId("U0123ABC456")
                .build();

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400 INVALID_INPUT을 반환한다")
    void signUp_passwordTooShort() throws Exception {
        ReqSignUpDto request = ReqSignUpDto.builder()
                .email("healthuser@example.com")
                .password("short")
                .nickname("닉네임")
                .slackId("U0123ABC456")
                .build();

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("닉네임이 비어 있으면 400 INVALID_INPUT을 반환한다")
    void signUp_blankNickname() throws Exception {
        ReqSignUpDto request = ReqSignUpDto.builder()
                .email("healthuser@example.com")
                .password("myPassw0rd!")
                .nickname("")
                .slackId("U0123ABC456")
                .build();

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("이메일이 이미 사용 중이면 409 EMAIL_DUPLICATED를 반환한다")
    void signUp_duplicateEmail() throws Exception {
        given(signUpService.signUp(any())).willThrow(new BaseException(UserErrorCode.EMAIL_DUPLICATED));

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("닉네임이 이미 사용 중이면 409 NICKNAME_DUPLICATED를 반환한다")
    void signUp_duplicateNickname() throws Exception {
        given(signUpService.signUp(any())).willThrow(new BaseException(UserErrorCode.NICKNAME_DUPLICATED));

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
    }

    private ReqLoginDto validLoginRequest() {
        return ReqLoginDto.builder()
                .email("healthuser@example.com")
                .password("myPassw0rd!")
                .build();
    }

    @Test
    @DisplayName("이메일/비밀번호가 일치하면 200과 함께 토큰 정보를 반환한다")
    void login_success() throws Exception {
        LoginResult result = LoginResult.builder()
                .accessToken("access-token")
                .refreshToken("8f3c1e2a-7b4d-4c9e-9a11-3f6d9c0b7e33")
                .tokenType("Bearer")
                .expiresIn(1800L)
                .build();
        given(loginService.login(any())).willReturn(result);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("8f3c1e2a-7b4d-4c9e-9a11-3f6d9c0b7e33"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400 INVALID_INPUT을 반환한다")
    void login_invalidEmail() throws Exception {
        ReqLoginDto request = ReqLoginDto.builder()
                .email("not-an-email")
                .password("myPassw0rd!")
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400 INVALID_INPUT을 반환한다")
    void login_passwordTooShort() throws Exception {
        ReqLoginDto request = ReqLoginDto.builder()
                .email("healthuser@example.com")
                .password("short")
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("이메일/비밀번호가 일치하지 않으면(또는 탈퇴한 계정이면) 401 LOGIN_FAILED를 반환한다")
    void login_loginFailed() throws Exception {
        given(loginService.login(any())).willThrow(new BaseException(UserErrorCode.LOGIN_FAILED));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("정지된 계정이면 403 ACCOUNT_SUSPENDED를 반환한다")
    void login_accountSuspended() throws Exception {
        given(loginService.login(any())).willThrow(new BaseException(UserErrorCode.ACCOUNT_SUSPENDED));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("X-User-Id/X-User-Role 헤더가 있으면 200과 함께 내 계정 정보를 반환한다")
    void getMyAccount_success() throws Exception {
        UUID userId = UUID.randomUUID();
        GetMyAccountResult result = GetMyAccountResult.builder()
                .id(userId)
                .email("healthuser@example.com")
                .nickname("헬스퀘스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .slackId("U0123ABC456")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        given(getMyAccountService.getMyAccount(userId)).willReturn(result);

        mockMvc.perform(get(ME_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("healthuser@example.com"))
                .andExpect(jsonPath("$.nickname").value("헬스퀘스트유저"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.slackId").value("U0123ABC456"));
    }

    @Test
    @DisplayName("X-User-Role이 ADMIN이면 403 FORBIDDEN을 반환한다")
    void getMyAccount_adminForbidden() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("X-User-Role이 USER가 아니면 403 FORBIDDEN을 반환한다")
    void getMyAccount_disallowedRole() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("X-User-Id/X-User-Role 헤더가 둘 다 없으면 401 UNAUTHORIZED를 반환한다")
    void getMyAccount_noHeaders() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("X-User-Role 헤더만 없으면 401 UNAUTHORIZED를 반환한다")
    void getMyAccount_missingRoleHeader() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않거나 탈퇴한 사용자면 404 USER_NOT_FOUND를 반환한다")
    void getMyAccount_userNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        given(getMyAccountService.getMyAccount(userId)).willThrow(new BaseException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get(ME_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
