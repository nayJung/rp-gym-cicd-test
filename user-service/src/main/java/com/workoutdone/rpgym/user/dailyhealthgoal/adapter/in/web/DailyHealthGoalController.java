package com.workoutdone.rpgym.user.dailyhealthgoal.adapter.in.web;

import com.workoutdone.rpgym.common.constant.HeaderConstants;
import com.workoutdone.rpgym.common.security.RequireRole;
import com.workoutdone.rpgym.common.security.UserRole;
import com.workoutdone.rpgym.user.dailyhealthgoal.adapter.in.web.dto.ReqRegisterDailyHealthGoalDto;
import com.workoutdone.rpgym.user.dailyhealthgoal.adapter.in.web.dto.ResDailyHealthGoalDto;
import com.workoutdone.rpgym.user.dailyhealthgoal.application.RegisterDailyHealthGoalResult;
import com.workoutdone.rpgym.user.dailyhealthgoal.application.RegisterDailyHealthGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-goals")
@RequiredArgsConstructor
public class DailyHealthGoalController {

    private final RegisterDailyHealthGoalService registerDailyHealthGoalService;

    // 본인 명의로만 등록 가능하므로 USER role만 허용 (ADMIN 제외)
    // X-User-Role 검증은 RoleAuthorizationInterceptor가 처리
    @RequireRole(UserRole.USER)
    @PostMapping("/me")
    public ResponseEntity<ResDailyHealthGoalDto> registerDailyHealthGoal(
            @RequestHeader(HeaderConstants.USER_ID) UUID userId,
            @Valid @RequestBody ReqRegisterDailyHealthGoalDto request
    ) {
        RegisterDailyHealthGoalResult result = registerDailyHealthGoalService.registerDailyHealthGoal(request.toCommand(userId));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ResDailyHealthGoalDto.from(result));
    }
}
