package com.workoutdone.rpgym.health.outbox.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.workoutdone.rpgym.health.outbox.domain.EventOutbox;
import com.workoutdone.rpgym.health.outbox.domain.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.schemas=health_service",
        "spring.jpa.properties.hibernate.default_schema=health_service",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("event_outbox 발행 순서")
class EventOutboxOrderingTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EventOutboxJpaRepository eventOutboxJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 트랜잭션에 적재된 이벤트는 물리적 순서가 뒤바뀌어도 적재 순서대로 조회된다")
    void returnsEventsInInsertionOrder() {

        UUID activityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // given: 하나의 INSERT 문으로 두 이벤트를 적재한다.
        //        HEALTH_ACTIVITY_SYNCED가 먼저, QUEST_SUGGESTED가 나중이다.
        insertTwoPendingEvents(activityId, userId);

        // created_at이 실제로 동일한지 먼저 확인한다. 이것이 타이브레이커가 필요한 이유다.
        Long distinctCreatedAt = jdbcTemplate.queryForObject(
                "select count(distinct created_at) from health_service.event_outbox",
                Long.class);
        assertThat(distinctCreatedAt)
                .as("같은 트랜잭션의 두 행은 created_at이 동일하다")
                .isEqualTo(1L);

        // when: 먼저 적재된 행을 갱신해 힙에서의 물리적 위치를 뒤로 밀어낸다.
        //       PostgreSQL은 UPDATE 시 값이 같아도 새 튜플을 테이블 끝에 추가하므로,
        //       순차 스캔 결과는 QUEST_SUGGESTED가 앞서게 된다.
        //       정렬 키가 created_at 하나뿐이면 이 상태에서 순서가 역전될 수 있다.
        jdbcTemplate.update(
                "update health_service.event_outbox set retry_count = retry_count "
                        + "where event_type = 'HEALTH_ACTIVITY_SYNCED'");

        List<EventOutbox> pending =
                eventOutboxJpaRepository.findByStatusForUpdate(OutboxStatus.PENDING, PageRequest.of(0, 10));

        // then
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).getSeq())
                .as("seq 오름차순으로 조회되어야 한다")
                .isLessThan(pending.get(1).getSeq());
        assertThat(pending.get(0).getEventType().name())
                .isEqualTo("HEALTH_ACTIVITY_SYNCED");
        assertThat(pending.get(1).getEventType().name())
                .isEqualTo("QUEST_SUGGESTED");
    }

    /**
     * 하나의 INSERT 문으로 두 행을 적재한다.
     * 단일 문이므로 두 행의 CURRENT_TIMESTAMP는 동일한 값이 되고,
     * seq는 VALUES에 나열한 순서대로 부여된다.
     */
    private void insertTwoPendingEvents(UUID activityId, UUID userId) {
        jdbcTemplate.update("""
                        insert into health_service.event_outbox
                            (outbox_id, event_id, event_type, source_activity_id,
                             dedup_key, partition_key, payload, status)
                        values
                            (?, ?, 'HEALTH_ACTIVITY_SYNCED', ?, ?, ?, '{}'::jsonb, 'PENDING'),
                            (?, ?, 'QUEST_SUGGESTED',        ?, ?, ?, '{}'::jsonb, 'PENDING')
                        """,
                UUID.randomUUID(), UUID.randomUUID(), activityId,
                "synced-" + activityId, userId.toString(),
                UUID.randomUUID(), UUID.randomUUID(), activityId,
                "suggested-" + activityId, userId.toString());
    }
}