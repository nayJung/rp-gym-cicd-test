package com.workoutdone.rpgym.user.user.adapter.in.web.dto;

import com.workoutdone.rpgym.user.user.application.RefreshTokenCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReqRefreshTokenDto {

    @NotBlank(message = "리프레시 토큰을 입력해주세요.")
    @UUID(message = "리프레시 토큰 형식이 올바르지 않습니다.")
    private String refreshToken;

    public RefreshTokenCommand toCommand() {
        return RefreshTokenCommand.builder()
                .refreshToken(refreshToken)
                .build();
    }
}
