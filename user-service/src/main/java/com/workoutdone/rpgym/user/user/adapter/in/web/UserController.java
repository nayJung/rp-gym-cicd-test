package com.workoutdone.rpgym.user.user.adapter.in.web;

import com.workoutdone.rpgym.common.constant.HeaderConstants;
import com.workoutdone.rpgym.common.security.RequireRole;
import com.workoutdone.rpgym.common.security.UserRole;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ReqLoginDto;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ReqSignUpDto;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ReqUpdateMyAccountDto;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ResLoginDto;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ResMyAccountDto;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ResSignUpDto;
import com.workoutdone.rpgym.user.user.adapter.in.web.dto.ResUpdateMyAccountDto;
import com.workoutdone.rpgym.user.user.application.GetMyAccountResult;
import com.workoutdone.rpgym.user.user.application.GetMyAccountService;
import com.workoutdone.rpgym.user.user.application.LoginResult;
import com.workoutdone.rpgym.user.user.application.LoginService;
import com.workoutdone.rpgym.user.user.application.SignUpResult;
import com.workoutdone.rpgym.user.user.application.SignUpService;
import com.workoutdone.rpgym.user.user.application.UpdateMyAccountResult;
import com.workoutdone.rpgym.user.user.application.UpdateMyAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final SignUpService signUpService;
    private final LoginService loginService;
    private final GetMyAccountService getMyAccountService;
    private final UpdateMyAccountService updateMyAccountService;

    @PostMapping("/signup")
    public ResponseEntity<ResSignUpDto> signUp(@Valid @RequestBody ReqSignUpDto request) {
        SignUpResult result = signUpService.signUp(request.toCommand());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ResSignUpDto.from(result));
    }

    @PostMapping("/login")
    public ResponseEntity<ResLoginDto> login(@Valid @RequestBody ReqLoginDto request) {
        LoginResult result = loginService.login(request.toCommand());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResLoginDto.from(result));
    }

    // USER role만 본인 계정 조회 가능 (ADMIN 제외)
    // X-User-Role 검증은 RoleAuthorizationInterceptor가 처리
    @RequireRole(UserRole.USER)
    @GetMapping("/me")
    public ResponseEntity<ResMyAccountDto> getMyAccount(
            @RequestHeader(HeaderConstants.USER_ID) UUID userId
    ) {
        GetMyAccountResult result = getMyAccountService.getMyAccount(userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResMyAccountDto.from(result));
    }

    // USER role만 본인 계정 수정 가능 (ADMIN 제외)
    // X-User-Role 검증은 RoleAuthorizationInterceptor가 처리
    @RequireRole(UserRole.USER)
    @PatchMapping("/me")
    public ResponseEntity<ResUpdateMyAccountDto> updateMyAccount(
            @RequestHeader(HeaderConstants.USER_ID) UUID userId,
            @Valid @RequestBody ReqUpdateMyAccountDto request
    ) {
        UpdateMyAccountResult result = updateMyAccountService.updateMyAccount(request.toCommand(userId));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ResUpdateMyAccountDto.from(result));
    }
}
