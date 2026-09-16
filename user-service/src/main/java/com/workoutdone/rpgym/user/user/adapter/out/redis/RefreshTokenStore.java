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
     * 기존 refreshToken 존재 확인 + 삭제 + 새 refreshToken 저장을 한 번의 Redis 명령으로 묶는다.
     * Java에서 DEL/SET을 따로 호출하면 그 사이에 커넥션이 끊기거나 프로세스가 죽었을 때 "삭제는 됐는데 저장은 안 됨" 상태가 남을 수 있음
     * Lua 스크립트는 Redis 서버 안에서 다른 명령이 끼어들 틈 없이 원자적으로 실행되므로 그 틈을 없앰
     *
     * EXISTS로 다시 한번 확인하는 이유(RefreshTokenService에서 findUserId로 이미 한 번 확인했는데도):
     * 그 확인과 이 스크립트 실행 사이에는 여전히 틈이 있어서, 동일한 refreshToken으로 동시에 요청이
     * 두 개 들어오면(예: 클라이언트가 병렬 API 호출 여러 개에서 각각 401을 받고 동시에 재발급을
     * 시도하는 경우) 둘 다 findUserId에서 "유효함"을 확인한 뒤 여기까지 올 수 있음.
     * 이때 무조건 DEL+SET만 하는 스크립트라면 두 요청 모두 성공해서 토큰 하나에서 새 토큰이
     * 두 개 나와버림(RTR이 보장해야 할 "토큰은 한 번만 쓸 수 있다"가 깨짐).
     * 여기서 EXISTS로 다시 확인하면, 먼저 실행된 요청이 이미 지운 뒤 실행되는 두 번째 요청은
     * EXISTS가 거짓이 되어 DEL/SET을 하지 않고 실패로 끝남. Redis는 스크립트 하나를 실행하는
     * 동안 다른 명령이 끼어들 수 없으므로, 두 요청이 동시에 도착해도 이 판정은 항상 안전함
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    // 재발급/로그아웃 시 유효성 검증을 위해 Redis에 저장 (key: refreshToken, value: userId)
    public void save(String refreshToken, UUID userId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, userId.toString(), TTL);
    }

    // 재발급/로그아웃 시 refreshToken으로 소유자 userId를 조회한다.
    // 만료/이미 폐기됨/존재한 적 없음을 여기서 구분하지 않는다 (모두 빈 값)
    public Optional<UUID> findUserId(String refreshToken) {
        String userId = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    // 로그아웃 시 해당 refreshToken 하나만 폐기하기 위해 사용
    public void delete(String refreshToken) {
        redisTemplate.delete(KEY_PREFIX + refreshToken);
    }

    // 토큰 회전(Refresh Token Rotation): 기존 refreshToken 존재 확인, 폐기, 새 refreshToken 저장을
    // Lua 스크립트로 원자적으로 실행한다 (셋 중 일부만 반영되는 상태를 만들지 않음)
    // 반환값이 false면 그 사이 다른 요청이 이미 같은 토큰을 회전시켜서 아무것도 하지 않았다는 뜻이다
    public boolean rotate(String oldRefreshToken, String newRefreshToken, UUID userId) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(KEY_PREFIX + oldRefreshToken, KEY_PREFIX + newRefreshToken),
                userId.toString(),
                String.valueOf(TTL.toSeconds())
        );
        return result != null && result == 1L;
    }
}
