package com.workoutdone.rpgym.user.user.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.user.adapter.out.jwt.JwtProvider;
import com.workoutdone.rpgym.user.user.adapter.out.redis.RefreshTokenStore;
import com.workoutdone.rpgym.user.user.domain.User;
import com.workoutdone.rpgym.user.user.domain.UserErrorCode;
import com.workoutdone.rpgym.user.user.domain.UserRepository;
import com.workoutdone.rpgym.user.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private static final String TOKEN_TYPE = "Bearer";

    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public RefreshTokenResult refresh(RefreshTokenCommand command) {
        //redis에 refresh token 이 없는 경우
        UUID userId = refreshTokenStore.findUserId(command.getRefreshToken())
                .orElseThrow(() -> new BaseException(UserErrorCode.INVALID_REFRESH_TOKEN));

        // 토큰 발급 이후 탈퇴 처리된 경우도 존재하지 않는 리프레시 토큰과 동일하게 취급해,
        // 탈퇴 여부를 별도로 노출하지 않는다 (로그인 API의 LOGIN_FAILED와 동일한 원칙)
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BaseException(UserErrorCode.INVALID_REFRESH_TOKEN));

        // 정지는 탈퇴와 달리 계정 상태를 인지시켜야 하므로 구분해서 응답
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BaseException(UserErrorCode.ACCOUNT_SUSPENDED);
        }

        // 토큰 회전(Refresh Token Rotation): 기존 refreshToken은 폐기하고 새로 발급
        refreshTokenStore.delete(command.getRefreshToken());

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = UUID.randomUUID().toString();
        refreshTokenStore.save(newRefreshToken, user.getId());

        return RefreshTokenResult.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtProvider.getAccessTokenExpirySeconds())
                .build();
    }
}
