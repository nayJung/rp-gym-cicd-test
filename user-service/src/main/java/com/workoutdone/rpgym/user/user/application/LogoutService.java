package com.workoutdone.rpgym.user.user.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.common.exception.CommonErrorCode;
import com.workoutdone.rpgym.user.user.adapter.out.redis.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final RefreshTokenStore refreshTokenStore;

    public void logout(LogoutCommand command) {
        Optional<UUID> ownerId = refreshTokenStore.findUserId(command.getRefreshToken());

        // 이미 폐기됐거나 존재한 적 없는 토큰이면 그대로 성공 처리한다.
        // 로그아웃은 여러 번 호출돼도 최종 상태가 같아야 하므로(멱등) 에러로 취급하지 않는다.
        if (ownerId.isEmpty()) {
            return;
        }

        // 본인 소유가 아닌 refreshToken은 폐기하지 않고 거부한다.
        if (!ownerId.get().equals(command.getUserId())) {
            throw new BaseException(CommonErrorCode.FORBIDDEN);
        }

        refreshTokenStore.delete(command.getRefreshToken());
    }
}
