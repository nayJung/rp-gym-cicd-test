package com.workoutdone.rpgym.health.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.workoutdone.rpgym.health.outbox.adapter.out.persistence.EventOutboxRepositoryImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({EventOutboxRepositoryImpl.class, OutboxCleanupService.class})
@TestPropertySource(properties = {
        "spring.flyway.schemas=health_service",
        "spring.jpa.properties.hibernate.default_schema=health_service",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("event_outbox 정리 배치")
class OutboxCleanupServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OutboxCleanupService outboxCleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("보관 기간이 지난 PUBLISHED 행만 삭제하고 PENDING·FAILED는 남긴다")
    void deletesOnlyExpiredPublishedRows() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishedBefore = now.minusDays(7);

        // given
        UUID expired = insertRow("PUBLISHED", now.minusDays(10));   // 삭제 대상
        UUID recent = insertRow("PUBLISHED", now.minusDays(1));     // 보관 기간 이내
        UUID pending = insertRow("PENDING", null);                  // 미발행
        UUID failed = insertRow("FAILED", now.minusDays(10));       // 분석 대상이라 보존

        // when
        int deleted = outboxCleanupService.deleteOnce(publishedBefore, 100);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(exists(expired)).isFalse();
        assertThat(exists(recent)).isTrue();
        assertThat(exists(pending)).isTrue();
        assertThat(exists(failed)).isTrue();
    }

    @Test
    @DisplayName("batchSize보다 대상이 많으면 batchSize만큼만 삭제한다")
    void deletesAtMostBatchSize() {

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            insertRow("PUBLISHED", now.minusDays(10));
        }

        int deleted = outboxCleanupService.deleteOnce(now.minusDays(7), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(countAll()).isEqualTo(3);
    }

    private UUID insertRow(String status, LocalDateTime publishedAt) {
        UUID outboxId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update("""
                        insert into health_service.event_outbox
                            (outbox_id, event_id, event_type, source_activity_id,
                             dedup_key, partition_key, payload, status, published_at)
                        values (?, ?, 'HEALTH_ACTIVITY_SYNCED', ?, ?, ?, '{}'::jsonb, ?, ?)
                        """,
                outboxId, UUID.randomUUID(), UUID.randomUUID(),
                "cleanup-" + outboxId, userId.toString(), status, publishedAt);

        return outboxId;
    }

    private boolean exists(UUID outboxId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from health_service.event_outbox where outbox_id = ?",
                Long.class, outboxId);
        return count != null && count > 0;
    }

    private Long countAll() {
        return jdbcTemplate.queryForObject(
                "select count(*) from health_service.event_outbox", Long.class);
    }
}