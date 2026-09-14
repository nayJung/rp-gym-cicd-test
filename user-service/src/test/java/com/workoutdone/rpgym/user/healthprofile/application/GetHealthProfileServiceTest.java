package com.workoutdone.rpgym.user.healthprofile.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfile;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfileErrorCode;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetHealthProfileServiceTest {

    @Mock
    private HealthProfileRepository healthProfileRepository;

    @InjectMocks
    private GetHealthProfileService getHealthProfileService;

    @Test
    @DisplayName("활성 프로필이 있으면 바디 프로필 정보를 반환한다")
    void getHealthProfile_success() {
        UUID userId = UUID.randomUUID();
        HealthProfile healthProfile = HealthProfile.create(userId, BigDecimal.valueOf(170.5), BigDecimal.valueOf(65.2));
        given(healthProfileRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(healthProfile));

        GetHealthProfileResult result = getHealthProfileService.getHealthProfile(userId);

        assertThat(result.getHeight()).isEqualByComparingTo(BigDecimal.valueOf(170.5));
        assertThat(result.getWeight()).isEqualByComparingTo(BigDecimal.valueOf(65.2));
    }

    @Test
    @DisplayName("등록된 프로필이 없으면 HEALTH_PROFILE_NOT_FOUND 예외를 던진다")
    void getHealthProfile_notRegistered() {
        UUID userId = UUID.randomUUID();
        given(healthProfileRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> getHealthProfileService.getHealthProfile(userId))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(HealthProfileErrorCode.HEALTH_PROFILE_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 프로필만 있으면 HEALTH_PROFILE_NOT_FOUND 예외를 던진다")
    void getHealthProfile_deletedProfile() {
        // findByUserIdAndDeletedAtIsNull은 deletedAt이 있는 프로필을 애초에 조회하지 않으므로
        // Repository가 Optional.empty()를 반환하는 것으로 시뮬레이션한다.
        UUID userId = UUID.randomUUID();
        given(healthProfileRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> getHealthProfileService.getHealthProfile(userId))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(HealthProfileErrorCode.HEALTH_PROFILE_NOT_FOUND));
    }
}
