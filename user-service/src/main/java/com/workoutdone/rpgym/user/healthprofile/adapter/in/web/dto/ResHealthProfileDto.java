package com.workoutdone.rpgym.user.healthprofile.adapter.in.web.dto;

import com.workoutdone.rpgym.user.healthprofile.application.GetHealthProfileResult;
import com.workoutdone.rpgym.user.healthprofile.application.RegisterHealthProfileResult;
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
public class ResHealthProfileDto {

    private UUID id;
    private BigDecimal height;
    private BigDecimal weight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ResHealthProfileDto from(RegisterHealthProfileResult result) {
        return ResHealthProfileDto.builder()
                .id(result.getId())
                .height(result.getHeight())
                .weight(result.getWeight())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }

    public static ResHealthProfileDto from(GetHealthProfileResult result) {
        return ResHealthProfileDto.builder()
                .id(result.getId())
                .height(result.getHeight())
                .weight(result.getWeight())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }
}
