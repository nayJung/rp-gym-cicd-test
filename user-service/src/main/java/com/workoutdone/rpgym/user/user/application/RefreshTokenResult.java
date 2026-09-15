package com.workoutdone.rpgym.user.user.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenResult {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
}
