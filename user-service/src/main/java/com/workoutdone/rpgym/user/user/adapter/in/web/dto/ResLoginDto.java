package com.workoutdone.rpgym.user.user.adapter.in.web.dto;

import com.workoutdone.rpgym.user.user.application.LoginResult;
import com.workoutdone.rpgym.user.user.application.RefreshTokenResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResLoginDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;

    public static ResLoginDto from(LoginResult result) {
        return ResLoginDto.builder()
                .accessToken(result.getAccessToken())
                .refreshToken(result.getRefreshToken())
                .tokenType(result.getTokenType())
                .expiresIn(result.getExpiresIn())
                .build();
    }

    public static ResLoginDto from(RefreshTokenResult result) {
        return ResLoginDto.builder()
                .accessToken(result.getAccessToken())
                .refreshToken(result.getRefreshToken())
                .tokenType(result.getTokenType())
                .expiresIn(result.getExpiresIn())
                .build();
    }
}
