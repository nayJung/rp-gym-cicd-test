package com.workoutdone.rpgym.user.user.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoal;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoalRepository;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfile;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfileRepository;
import com.workoutdone.rpgym.user.user.adapter.out.redis.RefreshTokenStore;
import com.workoutdone.rpgym.user.user.domain.User;
import com.workoutdone.rpgym.user.user.domain.UserErrorCode;
import com.workoutdone.rpgym.user.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawService {

    private final UserRepository userRepository;
    private final HealthProfileRepository healthProfileRepository;
    private final DailyHealthGoalRepository dailyHealthGoalRepository;
    private final RefreshTokenStore refreshTokenStore;

    ////TO-DO: user_long_term_health_goals(장기 건강 목표) 관련 cascade soft delete는 장기 목표 기능이 구현되면 함께 추가한다.
    public void withdraw(UUID userId) {
        // deletedAt 조건 없이 조회해서 "이미 탈퇴됨"과 "애초에 존재하지 않음"을 구분한다.
        // 전자는 재시도/다중 탭 등으로 정상적으로 재호출될 수 있는 멱등 대상이지만,
        // 후자는 게이트웨이가 인증한 요청인데도 id가 없는 이례적인 상태이므로 그대로 성공 처리하면 안 된다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(UserErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            return;
        }

        user.withdraw();
        userRepository.save(user);

        //회원 탈퇴 시, 연관 데이터(바디 프로필, 장기/일일 건강 목표)도 함께 삭제(soft delete) 처리함
        healthProfileRepository.findByUserIdAndDeletedAtIsNull(userId)
                .ifPresent(this::withdrawHealthProfile);

        dailyHealthGoalRepository.findByUserIdAndDeletedAtIsNull(userId)
                .ifPresent(this::withdrawDailyHealthGoal);

        /**
         * Redis는 JPA 트랜잭션 리소스가 아니라서 @Transactional에 함께 묶이지 않는다.
         * 여기서 바로 호출하면 DB 커밋이 이후에 실패(롤백)해도 Redis 삭제는 이미 반영되어
         * "탈퇴는 안 됐는데 세션만 끊김" 같은 불일치가 생길 수 있어, DB 커밋 성공 이후로 미룬다.
         * (반대로 DB 커밋 후 이 호출 자체가 실패하는 경우는 문제 없음 — /refresh가 결국 DB의
         * deletedAt을 다시 검사하므로 재발급은 막히고, 남은 토큰은 TTL로 자연 만료된다)
         */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 탈퇴 이후 재발급을 통한 세션 연장을 막기 위해, 다른 기기/세션에서 발급된 것까지 포함해 모두 폐기
                refreshTokenStore.deleteAllByUserId(userId);
            }
        });
    }

    private void withdrawHealthProfile(HealthProfile healthProfile) {
        healthProfile.delete();
        healthProfileRepository.save(healthProfile);
    }

    private void withdrawDailyHealthGoal(DailyHealthGoal dailyHealthGoal) {
        dailyHealthGoal.delete();
        dailyHealthGoalRepository.save(dailyHealthGoal);
    }
}
