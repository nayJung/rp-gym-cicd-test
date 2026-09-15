package com.workoutdone.rpgym.user.user.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
//Refresh Token 저장
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh-token:";
    private static final Duration TTL = Duration.ofDays(7); //Refresh Token 만료시간(7일)

    /*
     * 기존 refreshToken 삭제 + 새 refreshToken 저장을 한 번의 Redis 명령으로 묶는다.
     * Java에서 DEL/SET을 따로 호출하면 그 사이에 커넥션이 끊기거나 프로세스가 죽었을 때 "삭제는 됐는데 저장은 안 됨" 상태가 남을 수 있음
     * Lua 스크립트는 Redis 서버 안에서 다른 명령이 끼어들 틈 없이 원자적으로 실행되므로 그 틈을 없앰
     */
    private static final RedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('DEL', KEYS[1])
            return redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            """,
            String.class
    );

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

    // 토큰 회전(Refresh Token Rotation): 기존 refreshToken 폐기와 새 refreshToken 저장을
    // Lua 스크립트로 원자적으로 실행한다 (둘 중 하나만 반영되는 상태를 만들지 않음)
    public void rotate(String oldRefreshToken, String newRefreshToken, UUID userId) {
        redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(KEY_PREFIX + oldRefreshToken, KEY_PREFIX + newRefreshToken),
                userId.toString(),
                String.valueOf(TTL.toSeconds())
        );
    }
}
