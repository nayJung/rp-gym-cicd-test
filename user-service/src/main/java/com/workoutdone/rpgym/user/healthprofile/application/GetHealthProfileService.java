package com.workoutdone.rpgym.user.healthprofile.application;

import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfile;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfileErrorCode;
import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetHealthProfileService {

    private final HealthProfileRepository healthProfileRepository;

    public GetHealthProfileResult getHealthProfile(UUID userId) {
        HealthProfile healthProfile = healthProfileRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BaseException(HealthProfileErrorCode.HEALTH_PROFILE_NOT_FOUND));

        return GetHealthProfileResult.from(healthProfile);
    }
}
