package com.workoutdone.rpgym.user.dailyhealthgoal.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoal;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoalErrorCode;
import com.workoutdone.rpgym.user.dailyhealthgoal.domain.DailyHealthGoalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetDailyHealthGoalServiceTest {

    @Mock
    private DailyHealthGoalRepository dailyHealthGoalRepository;

    @InjectMocks
    private GetDailyHealthGoalService getDailyHealthGoalService;

    @Test
    @DisplayName("활성 목표가 있으면 일일 목표 정보를 반환한다")
    void getDailyHealthGoal_success() {
        UUID userId = UUID.randomUUID();
        DailyHealthGoal dailyHealthGoal = DailyHealthGoal.create(userId, 5000, 60, 500);
        given(dailyHealthGoalRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(dailyHealthGoal));

        GetDailyHealthGoalResult result = getDailyHealthGoalService.getDailyHealthGoal(userId);

        assertThat(result.getStepGoal()).isEqualTo(5000);
        assertThat(result.getActiveMinutesGoal()).isEqualTo(60);
        assertThat(result.getActiveCaloriesGoal()).isEqualTo(500);
    }

    @Test
    @DisplayName("등록된 목표가 없으면 DAILY_GOAL_NOT_FOUND 예외를 던진다")
    void getDailyHealthGoal_notRegistered() {
        UUID userId = UUID.randomUUID();
        given(dailyHealthGoalRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> getDailyHealthGoalService.getDailyHealthGoal(userId))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(DailyHealthGoalErrorCode.DAILY_GOAL_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 목표만 있으면 DAILY_GOAL_NOT_FOUND 예외를 던진다")
    void getDailyHealthGoal_deletedGoal() {
        // findByUserIdAndDeletedAtIsNull은 deletedAt이 있는 목표를 애초에 조회하지 않으므로
        // Repository가 Optional.empty()를 반환하는 것으로 시뮬레이션한다.
        UUID userId = UUID.randomUUID();
        given(dailyHealthGoalRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> getDailyHealthGoalService.getDailyHealthGoal(userId))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(DailyHealthGoalErrorCode.DAILY_GOAL_NOT_FOUND));
    }
}
