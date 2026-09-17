package com.workoutdone.rpgym.gateway.infrastructure.security;

import com.workoutdone.rpgym.common.jwt.JwtClaimConstants;
import com.workoutdone.rpgym.common.security.UserRole;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

@Component
public class RequiredClaimsValidator implements OAuth2TokenValidator<Jwt> {

    // 필수 Claim이 없거나 유효하지 않은 JWT를 처리하기 위한 오류
    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error("invalid_token");

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {

        // 사용자 식별에 필요한 sub Claim이 존재하는지 검증
        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        // sub Claim이 UUID 형식인지 검증
        try {
            UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        // 권한 정보에 필요한 role Claim이 존재하는지 검증
        String role = jwt.getClaimAsString(JwtClaimConstants.ROLE);

        if (role == null || role.isBlank()) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        // 프로젝트에서 정의한 USER / ADMIN Role인지 검증
        boolean validRole = Arrays.stream(UserRole.values())
                .anyMatch(userRole -> userRole.name().equals(role));

        if (!validRole) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        // 필수 Claim과 Role 검증을 모두 통과하면 인증 성공
        return OAuth2TokenValidatorResult.success();
    }
}