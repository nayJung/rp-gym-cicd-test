package com.workoutdone.rpgym.user.healthprofile.application;

import com.workoutdone.rpgym.user.healthprofile.domain.HealthProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetHealthProfileResult {

    private UUID id;
    private BigDecimal height;
    private BigDecimal weight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GetHealthProfileResult from(HealthProfile healthProfile) {
        return GetHealthProfileResult.builder()
                .id(healthProfile.getId())
                .height(healthProfile.getHeight())
                .weight(healthProfile.getWeight())
                .createdAt(healthProfile.getCreatedAt())
                .updatedAt(healthProfile.getUpdatedAt())
                .build();
    }
}
