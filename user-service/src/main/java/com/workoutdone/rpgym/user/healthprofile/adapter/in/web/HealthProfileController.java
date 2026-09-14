package com.workoutdone.rpgym.user.healthprofile.adapter.in.web;

import com.workoutdone.rpgym.common.constant.HeaderConstants;
import com.workoutdone.rpgym.common.exception.BaseException;
import com.workoutdone.rpgym.common.exception.CommonErrorCode;
import com.workoutdone.rpgym.common.security.RequireRole;
import com.workoutdone.rpgym.common.security.UserRole;
import com.workoutdone.rpgym.user.healthprofile.adapter.in.web.dto.ReqRegisterHealthProfileDto;
import com.workoutdone.rpgym.user.healthprofile.adapter.in.web.dto.ResHealthProfileDto;
import com.workoutdone.rpgym.user.healthprofile.application.GetHealthProfileResult;
import com.workoutdone.rpgym.user.healthprofile.application.GetHealthProfileService;
import com.workoutdone.rpgym.user.healthprofile.application.RegisterHealthProfileResult;
import com.workoutdone.rpgym.user.healthprofile.application.RegisterHealthProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health-profiles")
@RequiredArgsConstructor
public class HealthProfileController {

    private final RegisterHealthProfileService registerHealthProfileService;
    private final GetHealthProfileService getHealthProfileService;

    // 본인 명의로만 등록 가능하므로 USER role만 허용 (ADMIN 제외)
    // X-User-Role 검증은 RoleAuthorizationInterceptor가 처리
    @RequireRole(UserRole.USER)
    @PostMapping("/me")
    public ResponseEntity<ResHealthProfileDto> registerHealthProfile(
            @RequestHeader(value = HeaderConstants.USER_ID, required = false) String userIdHeader,
            @Valid @RequestBody ReqRegisterHealthProfileDto request
    ) {
        UUID userId = resolveUserId(userIdHeader);
        RegisterHealthProfileResult result = registerHealthProfileService.registerHealthProfile(request.toCommand(userId));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ResHealthProfileDto.from(result));
    }

    // 본인 명의의 프로필만 조회 가능하므로 USER role만 허용 (ADMIN 제외)
    // X-User-Role 검증은 RoleAuthorizationInterceptor가 처리
    @RequireRole(UserRole.USER)
    @GetMapping("/me")
    public ResponseEntity<ResHealthProfileDto> getHealthProfile(
            @RequestHeader(value = HeaderConstants.USER_ID, required = false) String userIdHeader
    ) {
        UUID userId = resolveUserId(userIdHeader);
        GetHealthProfileResult result = getHealthProfileService.getHealthProfile(userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResHealthProfileDto.from(result));
    }

    //TO-DO: 게이트웨이에서 JWT 유효성 및 요청헤더 유효성 검증 로직 추가되면 해당 메서드는 삭제 예정
    // 게이트웨이를 거치지 않아 X-User-Id가 없거나 UUID 형식이 아니면 인증 안 된 요청으로 취급
    private UUID resolveUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new BaseException(CommonErrorCode.UNAUTHORIZED);
        }

        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new BaseException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
