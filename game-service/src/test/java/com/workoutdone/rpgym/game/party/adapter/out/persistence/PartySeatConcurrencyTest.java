package com.workoutdone.rpgym.game.party.adapter.out.persistence;

import com.workoutdone.rpgym.game.config.JpaAuditingConfig;
import com.workoutdone.rpgym.game.party.domain.PartyMetric;
import com.workoutdone.rpgym.game.party.domain.PartyStatus;
import com.workoutdone.rpgym.game.party.domain.PartyVisibility;
import com.workoutdone.rpgym.game.party.domain.aggregate.Party;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마지막 자리를 두고 10명이 동시에 들어오면 3명만 성공해야 한다 (1/4 → 4/4).
 * H2 · Mock 으로는 증명이 안 되는 것이라 실제 PostgreSQL 을 띄운다. Flyway 가 V1~V6 를 적용한다.
 *
 * Docker 가 없는 환경(CI 일부 · 로컬)에서는 실패가 아니라 건너뛴다 — disabledWithoutDocker.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.schemas=game_service"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @DataJpaTest 슬라이스는 일반 @Configuration 을 싣지 않는다. 안 실으면 @CreatedDate 가 비어 created_at NOT NULL 에 걸린다.
@Import(JpaAuditingConfig.class)
// @DataJpaTest 의 테스트 트랜잭션을 끈다. 켜 두면 메인 스레드의 INSERT 가 커밋되지 않아
// 다른 커넥션을 쓰는 워커 스레드에는 파티가 보이지 않고, reserveSeat 가 전부 0 이 된다.
// 대신 데이터가 컨테이너에 남으므로 각 테스트는 자기가 만든 id 로만 단언한다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PartySeatConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PartyJpaRepository partyJpa;
    @Autowired PlatformTransactionManager txManager;

    @DisplayName("동시에 10명이 reserveSeat 해도 정확히 3명만 성공하고 카운터는 4 에서 멈춘다")
    @Test
    void onlyThreeSeatsAreGiven() throws Exception {
        Instant now = Instant.now();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID partyId = tx.execute(s -> partyJpa.saveAndFlush(Party.create(
                UUID.randomUUID(), "race", UUID.randomUUID(), PartyVisibility.PUBLIC, PartyMetric.STEPS, 4, now,
                Duration.ofHours(24), Duration.ofDays(7))).getId());

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                // 각 스레드가 자기 트랜잭션에서 조건부 UPDATE 한 문장을 실행한다
                Integer affected = tx.execute(s -> partyJpa.reserveSeat(partyId, PartyStatus.RECRUITING, now));
                if (affected != null && affected == 1) {
                    success.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(3);
        Party after = tx.execute(s -> partyJpa.findById(partyId).orElseThrow());
        assertThat(after.getCurrentMember()).isEqualTo(4);
    }

    @DisplayName("같은 metric 의 RECRUITING PUBLIC 파티만 매칭 후보로 잡힌다")
    @Test
    void matchingCandidatesFilterByMetric() {
        Instant now = Instant.now();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID steps = tx.execute(s -> partyJpa.saveAndFlush(Party.create(
                UUID.randomUUID(), "steps", UUID.randomUUID(), PartyVisibility.PUBLIC, PartyMetric.STEPS, 4, now,
                Duration.ofHours(24), Duration.ofDays(7))).getId());
        UUID calories = tx.execute(s -> partyJpa.saveAndFlush(Party.create(
                UUID.randomUUID(), "calories", UUID.randomUUID(), PartyVisibility.PUBLIC, PartyMetric.ACTIVE_CALORIES, 4, now,
                Duration.ofHours(24), Duration.ofDays(7))).getId());
        UUID privateSteps = tx.execute(s -> partyJpa.saveAndFlush(Party.create(
                UUID.randomUUID(), "private-steps", UUID.randomUUID(), PartyVisibility.PRIVATE, PartyMetric.STEPS, 4, now,
                Duration.ofHours(24), Duration.ofDays(7))).getId());

        List<Party> candidates = tx.execute(s -> partyJpa.findMatchingCandidates(
                PartyMetric.STEPS, PartyVisibility.PUBLIC, PartyStatus.RECRUITING, now,
                org.springframework.data.domain.PageRequest.of(0, 10)));

        // 다른 테스트가 남긴 파티가 있을 수 있어 "정확히 이것만" 이 아니라 포함/제외로 본다
        assertThat(candidates).extracting(Party::getId)
                .contains(steps)
                .doesNotContain(calories, privateSteps);
    }
}
