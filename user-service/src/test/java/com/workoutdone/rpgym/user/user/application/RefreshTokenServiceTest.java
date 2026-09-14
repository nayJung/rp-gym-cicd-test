package com.workoutdone.rpgym.user.user.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.user.adapter.out.jwt.JwtProvider;
import com.workoutdone.rpgym.user.user.adapter.out.redis.RefreshTokenStore;
import com.workoutdone.rpgym.user.user.domain.User;
import com.workoutdone.rpgym.user.user.domain.UserErrorCode;
import com.workoutdone.rpgym.user.user.domain.UserRepository;
import com.workoutdone.rpgym.user.user.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String OLD_REFRESH_TOKEN = "8f3c1e2a-7b4d-4c9e-9a11-3f6d9c0b7e33";

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User activeUser(UUID id) {
        User user = User.create("healthuser@example.com", "encoded-password", "헬스퀘스트유저", "U0123ABC456");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private RefreshTokenCommand command() {
        return RefreshTokenCommand.builder()
                .refreshToken(OLD_REFRESH_TOKEN)
                .build();
    }

    @Test
    @DisplayName("유효한 refreshToken이고 활성 계정이면 토큰을 회전하고 새 토큰을 반환한다")
    void refresh_success() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        given(refreshTokenStore.findUserId(OLD_REFRESH_TOKEN)).willReturn(Optional.of(userId));
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken(userId, user.getRole())).willReturn("new-access-token");
        given(jwtProvider.getAccessTokenExpirySeconds()).willReturn(1800L);

        RefreshTokenResult result = refreshTokenService.refresh(command());

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getExpiresIn()).isEqualTo(1800L);
        assertThat(result.getRefreshToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isNotEqualTo(OLD_REFRESH_TOKEN);

        verify(refreshTokenStore).delete(OLD_REFRESH_TOKEN);
        verify(refreshTokenStore).save(result.getRefreshToken(), userId);
    }

    @Test
    @DisplayName("Redis에 없는(만료/폐기/존재한 적 없는) refreshToken이면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void refresh_tokenNotFound() {
        given(refreshTokenStore.findUserId(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.refresh(command()))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN));

        verify(jwtProvider, never()).createAccessToken(any(), any());
        verify(refreshTokenStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("토큰 발급 이후 탈퇴 처리된 계정이면 INVALID_REFRESH_TOKEN 예외를 던진다(탈퇴 여부는 노출하지 않음)")
    void refresh_withdrawnAccount() {
        UUID userId = UUID.randomUUID();
        given(refreshTokenStore.findUserId(OLD_REFRESH_TOKEN)).willReturn(Optional.of(userId));
        // findByIdAndDeletedAtIsNull은 deletedAt이 있는 계정을 애초에 조회하지 않으므로
        // Repository가 Optional.empty()를 반환하는 것으로 시뮬레이션한다.
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.refresh(command()))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN));

        verify(jwtProvider, never()).createAccessToken(any(), any());
        verify(refreshTokenStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("토큰 발급 이후 정지된 계정이면 ACCOUNT_SUSPENDED 예외를 던진다")
    void refresh_suspendedAccount() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        given(refreshTokenStore.findUserId(OLD_REFRESH_TOKEN)).willReturn(Optional.of(userId));
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> refreshTokenService.refresh(command()))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(UserErrorCode.ACCOUNT_SUSPENDED));

        verify(jwtProvider, never()).createAccessToken(any(), any());
        verify(refreshTokenStore, never()).delete(anyString());
    }
}
