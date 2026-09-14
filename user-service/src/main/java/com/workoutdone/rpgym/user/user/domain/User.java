package com.workoutdone.rpgym.user.user.domain;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedDeletedEntity;
import com.workoutdone.rpgym.common.security.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users", schema = "user_service")
public class User extends BaseCreatedUpdatedDeletedEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, length = 255, updatable = false)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "slack_id", nullable = false, length = 100)
    private String slackId;

    private User(String email, String password, String nickname, String slackId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.slackId = slackId;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    // password는 이미 해시된 값을 받는다. 평문 해싱은 application 계층(PasswordEncoder)의 책임이다.
    public static User create(String email, String encodedPassword, String nickname, String slackId) {
        return new User(normalizeEmail(email), encodedPassword, nickname, slackId);
    }

    // 이메일은 대소문자를 구분하지 않는 것으로 취급(User@example.com == user@example.com)
    // 저장 값과 중복/로그인 조회 조건이 항상 같은 규칙으로 비교되도록 여기서 정규화를 일원화
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeSlackId(String slackId) {
        this.slackId = slackId;
    }

    // password는 이미 해시된 값을 받는다. 평문 해싱은 application 계층(PasswordEncoder)의 책임이다.
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 탈퇴(deletedAt 설정)한 계정은 status 컬럼 값과 무관하게 항상 WITHDRAWN으로 취급
    // 다른 서비스(Health/Game 등)에 사용자 상태를 노출할 때 이 값을 사용
    public UserStatus getDisplayStatus() {
        return getDeletedAt() != null ? UserStatus.WITHDRAWN : status;
    }
}
