package com.workoutdone.rpgym.user.user.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
//Refresh Token 저장
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh-token:";
    private static final Duration TTL = Duration.ofDays(7); //Refresh Token 만료시간(7일)

    private final StringRedisTemplate redisTemplate;

    // 재발급/로그아웃 시 유효성 검증을 위해 Redis에 저장 (key: refreshToken, value: userId)
    public void save(String refreshToken, UUID userId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, userId.toString(), TTL);
    }

    // 재발급 시 refreshToken으로 발급 대상 userId를 조회한다.
    // 만료/로그아웃-재발급으로 인한 폐기/애초에 존재한 적 없음을 여기서 구분하지 않는다 (모두 빈 값)
    public Optional<UUID> findUserId(String refreshToken) {
        String userId = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    // 토큰 회전(Refresh Token Rotation) 시 기존 refreshToken을 폐기하기 위해 사용
    public void delete(String refreshToken) {
        redisTemplate.delete(KEY_PREFIX + refreshToken);
    }
}
