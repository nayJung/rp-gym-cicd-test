package com.workoutdone.rpgym.user.healthprofile.domain;

import com.workoutdone.rpgym.common.entity.BaseCreatedUpdatedDeletedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_health_profiles", schema = "user_service")
public class HealthProfile extends BaseCreatedUpdatedDeletedEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal height;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    // 낙관적 락
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private HealthProfile(UUID userId, BigDecimal height, BigDecimal weight) {
        this.userId = userId;
        this.height = height;
        this.weight = weight;
    }

    public static HealthProfile create(UUID userId, BigDecimal height, BigDecimal weight) {
        return new HealthProfile(userId, height, weight);
    }
}
